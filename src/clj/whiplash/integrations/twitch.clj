(ns whiplash.integrations.twitch
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [whiplash.integrations.common :as common]
            [clojure.tools.logging :as log]))

(def twitch-page-size 100)

;; TODO potentially get all pages like in pandascore
;; TODO add rate limit logging
;; TODO make this an interface and serve a fixture for tests
(defn- get-streams
  [client-id usernames]
  (client/get "https://api.twitch.tv/helix/streams"
              {:headers      {"Client-ID" client-id}
               :content-type :json
               :query-params {:first      (str twitch-page-size)
                              :user_login usernames}}))

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
             (assoc match :twitch/username (-> username
                                               string/lower-case
                                               (string/replace #" " ""))
                          :live_url (format "https://player.twitch.tv/?channel=%s" username))))
         matches)))

(defn- views-per-twitch-stream
  [matches]
  (let [usernames (keep :twitch/username matches)]
    (if (not-empty usernames)
      (->> usernames
           (get-streams "lcqp3mnqxolecsk3e3tvqcueb2sx8x")
           common/resp->body
           :data
           (map (fn [stream-info]
                  (let [relevant-info (-> stream-info
                                          (select-keys [:viewer_count :user_name]))]
                    (hash-map (:user_name relevant-info) (:viewer_count relevant-info)))))
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
