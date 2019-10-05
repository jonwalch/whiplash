(ns whiplash.integrations.pandascore
  (:require
    [whiplash.config :refer [env]]
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [whiplash.time :as time]))

(def base-url "https://api.pandascore.co/%s/matches?")

(def game-lookup
  {:csgo "csgo"
   :dota2 "dota2"
   :lol "lol"
   :overwatch "ow"
   :pubg "pubg"})

(defn resp->body
  [resp]
  (some-> resp :body (json/read-str :key-fn keyword)))

;; TODO use a request pool
(defn get-matches-request
  [url api-key page-number date-range]
  (client/get url {:headers      {"Authorization" api-key}
                   :query-params {"range[begin_at]" date-range
                                  "page[size]"   "100"
                                  "page[number]" (str page-number)}
                   :debug        true}))

(defn get-all-matches
  [url api-key date-range]
  (let [resp (get-matches-request url api-key 0 date-range)
        resp-body (resp->body resp)
        ;; TODO: catch and log potential parsInt error if called with nil
        total-pages (-> resp :headers (get "X-Total") Integer/parseInt)]
    (conj
      (pmap #(resp->body
               (get-matches-request url api-key % date-range))
            (range 1 (inc total-pages)))
      resp-body)))

(defn get-matches
  [api-key game]
  (assert (contains? game-lookup game))
  (let [game-string (get game-lookup game)
        url (format base-url game-string)
        start (-> (time/days-ago 1) time/date-iso-string)
        end (-> (time/days-in-future 1) time/date-iso-string)
        date-range (format "%s,%s" start end)]
    (flatten (get-all-matches url api-key date-range))))

(comment
  (def foo
    (get-matches "token" :csgo))
  foo
  (-> foo
      :body
      (json/read-str :key-fn keyword)
      first
      #_(select-keys [:status :name :winner :results :begin-at :end-at])
      )
  )
