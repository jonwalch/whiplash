(ns whiplash.integrations.twitch
  (:require [clj-http.client :as client]
            [clojure.string :as string]
            [whiplash.integrations.common :as common]))

(defn- get-streams
  [client-id usernames]
  (client/get "https://api.twitch.tv/helix/streams"
              {:headers      {"Client-ID" client-id}
               :content-type :json
               :query-params {:first      "100"
                              :user_login usernames}}))

(defn- add-twitch-usernames-from-urls
  [matches]
  (let [twitch-regex #"^https://player.twitch.tv/\?channel=(.*)$|^https://www.twitch.tv/(.*)$"]
    (map (fn [match]
           (let [regex-match (->> match
                                  :live_url
                                  (re-find twitch-regex))]
             ;; Second or third will not be nil for each vector of regex matches
             (assoc match :twitch_username (or (second regex-match)
                                               (nth regex-match 2)))))
         matches)))

(defn- views-per-twitch-stream
  [matches]
  (let [usernames (keep :twitch_username matches)]
    (->> (get-streams "lcqp3mnqxolecsk3e3tvqcueb2sx8x" usernames)
         common/resp->body
         :data
         (map (fn [stream-info]
                (let [relevant-info (-> stream-info
                                        (select-keys [:viewer_count :user_name])
                                        (update :user_name (fn [val]
                                                             (-> val
                                                                 string/lower-case
                                                                 (string/replace #" " "")))))]
                  (hash-map (:user_name relevant-info) (:viewer_count relevant-info)))))
         (apply conj))))

(defn add-live-viewer-count
  [matches]
  (let [matches (add-twitch-usernames-from-urls matches)
        views-lookup (views-per-twitch-stream matches)]
    (->> matches
         (map
           (fn [{:keys [twitch_username] :as match}]
             (let [live-viewers (get views-lookup twitch_username 0)]
               (assoc match :live_viewers live-viewers)))))))
