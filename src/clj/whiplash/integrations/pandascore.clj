(ns whiplash.integrations.pandascore
  (:require
    [whiplash.config :refer [env]]
    [clj-http.client :as client]
    [whiplash.time :as time]
    [whiplash.integrations.twitch :as twitch]
    [whiplash.integrations.common :as common]
    [clojure.string :as string]
    [java-time :as java-time]))

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
(defn get-matches-request
  [url api-key page-number date-range]
  (client/get url {:headers      {"Authorization" api-key}
                   :query-params {"range[begin_at]" date-range
                                  "page[size]"   (str pandascore-page-size)
                                  "page[number]" (str page-number)
                                  "sort" "begin_at"}}))

(defn get-all-matches
  [url api-key date-range]
  (let [resp (get-matches-request url api-key 0 date-range)
        resp-body (common/resp->body resp)
        ;; TODO: catch and log potential parsInt error if called with nil
        total-items (-> resp :headers (get "X-Total") Integer/parseInt)
        total-pages (if (pos-int? (rem total-items pandascore-page-size))
                      (+ 1 (quot total-items pandascore-page-size))
                      (quot total-items pandascore-page-size))]
    (if (= total-pages 1)
      (seq resp-body)
      (conj
        (pmap #(common/resp->body
                 (get-matches-request url api-key % date-range))
              (range 1 (inc total-pages)))
        resp-body))))

(defn get-matches
  [api-key game]
  (assert (contains? game-lookup game))
  (let [game-string (get game-lookup game)
        url (format matches-url game-string)
        start (time/date-iso-string (time/days-delta -1))
        end (time/date-iso-string (time/days-delta 1))
        date-range (format "%s,%s" start end)]
    (flatten (get-all-matches url api-key date-range))))

(defn transform-timestamps
  [matches]
  (let [update-fn #(when %
                     (time/timestamp-to-zdt %))
        also-update-fn (fn [val]
                         (-> val
                             (update :begin_at update-fn)
                             (update :end_at update-fn)
                             (update :scheduled_at update-fn)))]
    (map
      #(let [updated-games (mapv also-update-fn (:games %))
             updated-match (also-update-fn %)]
         (assoc updated-match :games updated-games))
      matches)))

(def match-keys
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

(defn best-stream-candidate
  []
  (->> (get-matches "rPMcxOQ-nPbL4rKOeZ8O8PBkZy6-0Ib4EAkHqxw2Gj16AvXuaJ4" :csgo)
       twitch-matches
       running-matches
       transform-timestamps
       twitch/add-live-viewer-count
       (sort by-viewers-and-scheduled)
       first))

(comment
  (def foo
    (get-matches "rPMcxOQ-nPbL4rKOeZ8O8PBkZy6-0Ib4EAkHqxw2Gj16AvXuaJ4" :csgo))
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

  (best-stream-candidate)

  (-> (client/get "https://api.twitch.tv/helix/streams"
                  {:headers      {"Client-ID" "lcqp3mnqxolecsk3e3tvqcueb2sx8x"}
                   :content-type :json
                   :query-params {:first "100"
                                  :user_login []}
                   :debug true})
      #_resp->body)
  )
