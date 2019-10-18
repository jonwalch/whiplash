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
(def tournaments-url (str base-url "tournaments"))

(def game-lookup
  {:csgo "csgo"
   :dota2 "dota2"
   :lol "lol"
   :overwatch "ow"
   :pubg "pubg"})

(def pandascore-page-size 100)

;; TODO use a request pool
;; TODO make this an interface and serve a fixture for tests
;; TODO get access key from KMS
(defn get-matches-request
  [url page-number date-range]
  (let [api-key "rPMcxOQ-nPbL4rKOeZ8O8PBkZy6-0Ib4EAkHqxw2Gj16AvXuaJ4"]
    (client/get url {:headers      {"Authorization" api-key}
                     :query-params {"range[begin_at]" date-range
                                    "page[size]"      (str pandascore-page-size)
                                    "page[number]"    (str page-number)
                                    "sort"            "begin_at"}})))

(defn get-all-matches
  [url date-range]
  (let [resp (get-matches-request url 0 date-range)
        resp-body (common/resp->body resp)
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
      (seq resp-body)
      (conj
        (pmap #(common/resp->body
                 (get-matches-request url % date-range))
              (range 1 (inc total-pages)))
        resp-body))))

(defn get-matches
  [game]
  (assert (contains? game-lookup game))
  (let [game-string (get game-lookup game)
        url (format matches-url game-string)
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

#_(def match-keys
  [:id :live_url :begin_at :end_at :games :name :opponents :scheduled_at :status])

(defn twitch-matches
  [matches]
  (filter #(when-let [url (:live_url %)]
            (string/includes? url "twitch"))
          matches))

(defn running-matches
  "Will also return soon to be running matches"
  [matches]
  (->> matches
       (filter (fn [{:keys [status]}]
                 (or (= "running" status)
                     (= "not_started" status))))))

(defn by-viewers-and-scheduled
  [x y]
  ;; Sort by largest view count
  (let [c (compare (:twitch/live-viewers y) (:twitch/live-viewers x))]
    (if (not= c 0)
      c
      ;; if same view count, sort by which one starts sooner
      (compare (:scheduled_at x) (:scheduled_at y)))))

;; TODO centralize this so there is one source of truth
;; currently different users could be watching different streams because each
(defn sort-and-transform-stream-candidates
  [pandascore-matches]
  (->> pandascore-matches
       twitch-matches
       running-matches
       transform-timestamps
       twitch/add-live-viewer-count
       (sort by-viewers-and-scheduled)))

(comment
  (def foo
    (get-matches :csgo))
  foo
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
