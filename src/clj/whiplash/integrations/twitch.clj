(ns whiplash.integrations.twitch
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [whiplash.integrations.common :as common]
            [clojure.tools.logging :as log]))

(def twitch-page-size 100)
;; TODO: Rotate and remove from source
(def twitch-client-id "lcqp3mnqxolecsk3e3tvqcueb2sx8x")
(def twitch-client-secret "t5lt4ikmp0ovweuhi6zsex1lic98ls")

(defn get-token
  []
  (client/post "https://id.twitch.tv/oauth2/token"
               {:content-type :json
                :query-params  {:client_id twitch-client-id
                                :client_secret twitch-client-secret
                                :grant_type "client_credentials"}}))

;; TODO potentially get all pages like in pandascore
;; TODO add rate limit logging
;; TODO make this an interface and serve a fixture for tests
(defn- get-streams
  [usernames]
  (client/get "https://api.twitch.tv/helix/streams"
              {:headers      {"Client-ID" twitch-client-id}
               :content-type :json
               :query-params {:first      (str twitch-page-size)
                              :user_login usernames}}))

;; https://dev.twitch.tv/docs/api/reference#get-users
(defn get-user-by-id
  [token user-id]
  (client/get "https://api.twitch.tv/helix/users"
              {:headers      {"Authorization" (format "Bearer %s" token)
                              "Client-ID" twitch-client-id}
               :content-type :json
               :query-params  {:id user-id}}))

(defn get-user-by-login
  [token login]
  (client/get "https://api.twitch.tv/helix/users"
              {:headers      {"Authorization" (format "Bearer %s" token)
                              "Client-ID" twitch-client-id}
               :content-type :json
               :query-params  {:login login}}))

(defn get-login-from-user-id
  [user-id]
  (try
    (some-> (get-user-by-id (some-> (get-token)
                                    common/resp->body
                                    :access_token)
                            user-id)
            common/resp->body
            :data
            first
            :login)
    (catch Throwable t (log/error t))))

(comment
  ;; Huddlesworth
  (get-login-from-user-id "207580146")
  (get-token)
  (get-user-by-id "2mypjnug57rjt9nui70vlnagnf8dor" "207580146")
  )

(defn- standarize-twitch-user-name
  [user-name]
  (some-> user-name
          string/lower-case
          (string/replace #" " "")))

(defn- add-twitch-usernames-and-url
  [matches]
  (let [twitch-regex #"^https:\/\/player\.twitch\.tv\/\?channel=(.+?)(?=&|$).*$|^https:\/\/www\.twitch\.tv\/(.+?)(?=&|$).*$"]
    (map (fn [{:keys [live_url] :as match}]
           (let [regex-match (re-find twitch-regex live_url)
                 ;; Second or third will not be nil for each vector of regex matches
                 username (or (second regex-match)
                              (nth regex-match 2))]
             (when-not username
               (log/info (format "couldn't parse twitch username from pandascore live_url %s" live_url)))
             (assoc match :twitch/username (standarize-twitch-user-name username)
                          :live_url (format "https://player.twitch.tv/?channel=%s" username))))
         matches)))

(defn- views-per-twitch-stream
  [matches]
  (let [usernames (keep :twitch/username matches)]
    (if (not-empty usernames)
      (->> usernames
           (get-streams)
           common/resp->body
           :data
           (map (fn [stream-info]
                  (let [relevant-info (-> stream-info
                                          (select-keys [:viewer_count :user_name]))]
                    (hash-map (standarize-twitch-user-name (:user_name relevant-info))
                              (:viewer_count relevant-info)))))
           (apply conj))
      {})))

(defn add-live-viewer-count
  [matches]
  (let [matches (add-twitch-usernames-and-url matches)
        views-lookup (views-per-twitch-stream matches)]
    (log/info "Twitch view lookup: " views-lookup)
    (->> matches
         (map
           (fn [{:keys [twitch/username] :as match}]
             (let [live-viewers (get views-lookup username 0)]
               (when (= 0 live-viewers)
                 (log/info (format "couldn't get view count for parsed twitch user %s" username)))
               (assoc match :twitch/live-viewers live-viewers)))))))
