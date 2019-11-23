(ns whiplash.integrations.pandascore
  (:require
    [whiplash.config :refer [env]]
    [clj-http.client :as client]
    [whiplash.time :as time]
    [whiplash.integrations.twitch :as twitch]
    [whiplash.integrations.common :as common]
    [clojure.string :as string]
    [clojure.tools.logging :as log]))

(def base-url "https://api.pandascore.co/%s/")
(def matches-url (str base-url "matches"))
(def game-url (str base-url "games"))
(def tournaments-url (str base-url "tournaments"))

(def pandascore-api-key "rPMcxOQ-nPbL4rKOeZ8O8PBkZy6-0Ib4EAkHqxw2Gj16AvXuaJ4")
(def pandascore-page-size 100)

(def game-lookup
  {:csgo "csgo"
   :dota2 "dota2"
   :lol "lol"
   :overwatch "ow"
   :pubg "pubg"})

;; TODO use a request pool
;; TODO get access key from KMS
(defn get-matches-request
  [url page-number date-range]
  (let [{:keys [body status] :as resp}
        (client/get url {:headers      {"Authorization" pandascore-api-key}
                         :query-params {"range[begin_at]" date-range
                                        "page[size]"      (str pandascore-page-size)
                                        "page[number]"    (str page-number)
                                        "sort"            "begin_at"}})]
    (when-not (= 200 status)
      (log/error "Pandascore failure" resp))
    (if (some? body)
      (assoc resp :body (common/resp->body resp))
      resp)))

(defn get-all-matches
  [url date-range]
  (let [{:keys [body] :as resp} (get-matches-request url 0 date-range)
        ;; TODO: catch and log potential parsInt error if called with nil
        total-items (-> resp :headers (get "X-Total") Integer/parseInt)
        total-pages (if (pos-int? (rem total-items pandascore-page-size))
                      (+ 1 (quot total-items pandascore-page-size))
                      (quot total-items pandascore-page-size))
        limit (-> resp :headers (get "X-Rate-Limit-Remaining") Integer/parseInt)]
    ;;TODO put this logging in a more accurate place
    (when (< limit 100)
      (log/warn (format "only %s pandscore requests left this hour" limit)))
    (if (= total-pages 1)
      (seq body)
      (conj
        (pmap #(common/resp->body
                 (get-matches-request url % date-range))
              (range 1 (inc total-pages)))
        body))))

(defn get-matches
  [game]
  (assert (contains? game-lookup game))
  (let [url (format matches-url (get game-lookup game))
        start (time/date-iso-string (time/days-delta -1))
        end (time/date-iso-string (time/days-delta 1))
        date-range (format "%s,%s" start end)]
    (flatten (get-all-matches url date-range))))

(defn transform-timestamps
  [matches]
  (let [update-fn #(when %
                     (time/timestamp-to-zdt %))
        update-timestamps-fn (fn [val]
                               (-> val
                                   (update :begin_at update-fn)
                                   (update :end_at update-fn)
                                   (update :scheduled_at update-fn)
                                   (update :modified_at update-fn)))]
    (map
      #(let [updated-games (mapv update-timestamps-fn (:games %))
             updated-match (update-timestamps-fn %)]
         (assoc updated-match :games updated-games))
      matches)))

(defn twitch-matches
  [matches]
  (filter #(when-let [url (:live_url %)]
            (string/includes? url "twitch"))
          matches))

(defn running-matches
  "Will also return soon to be running matches"
  [matches]
  (filter (fn [{:keys [status]}]
            (or (= "running" status)
                (= "not_started" status)))
          matches))

(defn add-current-game
  [matches]
  (map (fn [{:keys [games] :as match}]
         (let [match (assoc match :whiplash/current-game (first (running-matches games)))]
           ;; Added to fix shitty pandascore bug
           (if (= 1 (count games))
             (assoc-in match [:whiplash/current-game :begin_at] (:begin_at match))
             match)))
       matches))

(defn by-viewers-and-scheduled
  [x y]
  ;; Sort by largest view count
  (let [c (compare (:twitch/live-viewers y) (:twitch/live-viewers x))]
    (if (not= c 0)
      c
      ;; if same view count, sort by which one starts sooner
      (compare (:scheduled_at x) (:scheduled_at y)))))

;; TODO centralize this so there is one source of truth
(defn sort-and-transform-stream-candidates
  [pandascore-matches]
  (->> pandascore-matches
       twitch-matches
       running-matches
       transform-timestamps
       twitch/add-live-viewer-count
       add-current-game
       (sort by-viewers-and-scheduled)))

#_(comment
  (->> current
       ;twitch-matches
       running-matches
       first
       #_(map :scheduled_at))
  (def foo
    (get-matches :csgo))
  foo
  ;; TODO jonwalch pick up from here tomorrow
  (let [match->game-lookup (->> foo
                                (filter #(= "finished" (:status %)))
                                ;transform-timestamps
                                (map :games)
                                flatten
                                (group-by :match_id))
        update-txs (->> (db/find-all-unprocessed-bets)
                        (keep
                          (fn [guess]
                            (let [games-in-match (get match->game-lookup (:match/id guess))
                                  result {:db/id            (:db/id guess)
                                          :guess/score      0
                                          :guess/processed? true}]
                              (if (some? games-in-match)
                                (keep
                                  (fn [game]
                                    (when (= (:id game) (:game/id guess))
                                      (if (= (get-in game [:winner :id]) (:team/id guess))
                                        (assoc result :guess/score 100)
                                        result)))
                                  games-in-match)
                                (log/info (format "Match id %s not found in finished game lookup." (:match/id guess)))))))
                        flatten
                        vec)]
    (when (not-empty update-txs)
      (d/transact (:conn db/datomic-cloud) {:tx-data update-txs})))

  (let [running-matches (->> foo
                             twitch-matches
                             ;; TODO make sure its a twitch url too
                             #_(map (fn [match]
                                      (select-keys match match-keys)))
                             transform-timestamps
                             running-matches
                             twitch/add-live-viewer-count
                             (sort by-viewers-and-scheduled)
                             #_(filter (fn [m]
                                         (= (:twitch/username m) "beyondthesummit_pt")))
                             (map #(select-keys % [:twitch/live-viewers :live_url :twitch/username :slug :scheduled_at #_:end_at]))
                             ;(group-by :status)
                             )]
    running-matches)

  foo
  (sort-and-transform-stream-candidates foo)

  (-> (client/get "https://api.twitch.tv/helix/streams"
                  {:headers      {"Client-ID" "lcqp3mnqxolecsk3e3tvqcueb2sx8x"}
                   :content-type :json
                   :query-params {:first "100"
                                  :user_login []}
                   :debug true})
      #_resp->body)
  )

