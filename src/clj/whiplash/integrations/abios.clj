(ns whiplash.integrations.abios
  (:require
    [whiplash.config :refer [env]]
    [clj-http.client :as client]
    [whiplash.time :as time]
    [whiplash.integrations.common :as common]
    [clojure.tools.logging :as log]
    [mount.core :as mount]
    [java-time :as java-time]))

(mount/defstate cached-token
                :start
                (atom {})

                :stop
                (atom {}))

(defn- access-token-req
  []
  (log/info "Getting new Abios access token")
  (let [{:keys [status] :as resp}
        (client/post "https://api.abiosgaming.com/v2/oauth/access_token"
                     {#_#_:debug true
                      :form-params {"grant_type"    "client_credentials"
                                    "client_id"     "jonwalch"
                                    "client_secret" "df5bda2c14cb75b52b899de1972e1d14784ddbd8ca5d0a7bdd"}})]
    (when-not (= 200 status)
      (log/error "Abios access token failure " resp))
    (assoc resp :body (common/resp->body resp))))

(defn access-token
  "grants a new one every 50 minutes, good for an hour"
  []
  (let [{:keys [abios/token abios/token-expiry-date]} (deref cached-token)]
    (if (or (nil? token)
            (nil? token-expiry-date)
            (java-time/after? (time/minutes-delta 10) token-expiry-date))
      (let [{:keys [body status]} (access-token-req)
            {:keys [access_token expires_in]} body]
        (if (and (= 200 status) access_token expires_in)
          (do
            ;; New token lasts for 3600 seconds and Abios starts issuing new ones at 600 seconds
            (reset! cached-token {:abios/token             access_token
                                  :abios/token-expiry-date (time/seconds-delta (int expires_in))})
            access_token)
          (do
            (log/error (format "Couldn't retrieve Abios token, using previous value %s" token))
            token)))
      (do (log/info (format "using cached Abios token, expiry date %s" token-expiry-date))
          token))))

(defn get-csgo-series-request
  [{:keys [page-number is-over? token]}]
  ;; TODO starts after and starts before as parameters
  (let [{:keys [body status] :as resp}
        (client/get "https://api.abiosgaming.com/v2/series"
                    {;;:debug        true
                     :query-params {"page"          (str page-number)
                                    "games[]"       ["5"]   ;; 5 is CSGO
                                    "with[]"        ["matches" "casters"]
                                    "tiers[]"       ["1" "2"]
                                    "is_over"       is-over?
                                    "starts_after"  (time/date-iso-string (time/hours-delta -8))
                                    "starts_before" (time/date-iso-string (time/days-delta 5))
                                    "access_token"  token}})]
    (when-not (= 200 status)
      (log/error "Abios Series CSGO get failure" resp))
    (assoc resp :body (common/resp->body resp))))

(defn get-all-series
  []
  (let [token (access-token)
        args {:page-number 1
              :is-over? false
              :token token}
        {:keys [body]} (get-csgo-series-request {:page-number 1
                                                 :is-over? false
                                                 :token token})
        total-pages (:last_page body)
        data (:data body)]
    (if (= total-pages 1)
      (seq data)
      (->> (range 2 (inc total-pages))
           (pmap #(-> (assoc args :page-number %)
                      (get-csgo-series-request)
                      :body
                      :data))
           (apply concat data)))))

(comment
  (get-all-series)
         )

(defn best-caster
  "take a series' casters and return the most relevant one"
  [{:keys [casters] :as series}]
  (->> casters
       (filter (fn [{:keys [type] :as caster}]
                 ;; 1 is Twitch
                 (= 1 type)))
       (map (fn [{:keys [stream] :as caster}]
              ;; TODO also order by country/language
              {:twitch/viewer-count (:viewer_count stream)
               :twitch/username     (:username stream)}))
       (sort-by :twitch/viewer-count #(compare %2 %1))
       first))

(defn current-match
  "takes a series and picks the match that is happening
  soonest or is currently happening"
  [{:keys [matches] :as series}]
  (some #(when-not (:winner %)
           %)
        matches))

(defn get-match-pbp-light-summary
  [{:keys [match-id token]}]
  (let [{:keys [status] :as resp}
        (client/get (format "https://api.abiosgaming.com/v2/matches/%s/light_summary"
                            match-id)
                    {#_#_:debug        true
                     :query-params {"access_token" token}})]
    (when-not (= 200 status)
      (log/error "Abios light summary CSGO get failure" resp))
    (assoc resp :body (common/resp->body resp))))

(defn get-round-info
  "outputs a map with key: round number, and value: team id"
  [{:keys [match-id token] :as args}]
  (let [{:keys [body] :as resp} (get-match-pbp-light-summary args)
        round->winner (some->> body
                               :rounds
                               (map (fn [{:keys [round_nr t_side ct_side]}]
                                      {round_nr (cond
                                                  (:is_winner t_side)
                                                  (-> t_side :roster :id)

                                                  (:is_winner ct_side)
                                                  (-> ct_side :roster :id))}))
                               (apply merge))]
    {:winners       round->winner
     :current-round (->> round->winner
                         (sort-by first)
                         (some (fn [[round-number team-id]]
                                 (when (nil? team-id)
                                   round-number))))}))

(comment (get-round-info {:match-id 379650 :token (access-token)}))

(defn best-stream-candidates
  []
  (let [all-relevant-series (->> (get-all-series)
                                 (filter :streamed)
                                 (filter #(-> % :pbp_status nil? not))
                                 (map #(assoc % :twitch/best-caster
                                                (best-caster %)
                                                :whiplash/current-match
                                                (current-match %)))
                                 #_(filter #(and (-> % :whiplash/current-match nil? not)
                                                 (-> % :whiplash/best-caster nil? not)))
                                 #_(filter #(-> %
                                                :whiplash/current-match
                                                :has_pbpstats)))]
    all-relevant-series
    )
  )

(comment (best-stream-candidates))

;; 379650
(comment
  (-> (client/get "https://api.abiosgaming.com/v2/games"
                  {:debug        true
                   :query-params {"q"            "counter"
                                  "access_token" access-token}})
      common/resp->body)

  (get-csgo-series-request {:page-number  1
                            :is-over?     false
                            :access-token (access-token)}))

