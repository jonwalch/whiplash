(ns whiplash.test.common
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [whiplash.middleware.formats :as formats]
            [mount.core :as mount]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [whiplash.middleware :as middleware]
            [ring-ttl-session.core :refer [ttl-memory-store]]
            [whiplash.env :refer [defaults]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(def test-state
  (atom {}))

(defn internal-send-email-fake
  [params]
  (swap! test-state (fn [{:keys [emails] :as val}]
                      (assoc val :emails (conj emails params)))))

(defn twitch-get-token-fake
  []
  (-> (io/resource "fixtures/twitch/get-token.edn")
      slurp
      edn/read-string))

(defn twitch-get-user-name-fake
  [token user-id]
  (-> (io/resource "fixtures/twitch/get-user-id-huddy.edn")
      slurp
      edn/read-string))

(def ^:dynamic *twitch-streams-live?* true)

(defn twitch-get-streams-fake
  [token logins game-ids]
  (let [resource (if *twitch-streams-live?*
                   "fixtures/twitch/get-csgo-twitch-streamers-huddy.edn"
                   "fixtures/twitch/get-csgo-twitch-streamers-none.edn")]
    (-> (io/resource resource)
        slurp
        edn/read-string)))

(defn app-fixtures
  []
  (use-fixtures
    :once
    (fn [f]
      (mount/start #'whiplash.config/env
                   #'whiplash.handler/app-routes)
      (with-redefs [whiplash.integrations.amazon-ses/internal-send-email internal-send-email-fake
                    whiplash.integrations.twitch/get-token twitch-get-token-fake
                    whiplash.integrations.twitch/get-user-by-id twitch-get-user-name-fake
                    whiplash.integrations.twitch/get-live-streams twitch-get-streams-fake]
        (f))
      (mount/stop #'whiplash.config/env
                  #'whiplash.handler/app-routes))))

(defn db-fixtures
  []
  (use-fixtures
    :each
    (fn [f]
      (reset! test-state {})
      (mount/start #'whiplash.db.core/datomic-cloud)
      (f)
      (mount/stop #'whiplash.db.core/datomic-cloud))))

;; TODO: add fixture that puts resources in dist folder if it doesnt exist already

(defn twitch-view-fake
  [matches]
  (->> matches
       (map
         #(hash-map (:twitch/username %) 100))
       (apply conj)))

(defn test-app []
  (middleware/wrap-base #'whiplash.handler/app-routes))

(defn parse-json-body
  [{:keys [body] :as req}]
  (assert body)
  (m/decode formats/instance "application/json" body))

(defn pandascore-running-fake
  [url page-number date-range]
  (-> (io/resource "fixtures/pandascore-running-response.edn")
      slurp
      edn/read-string))

(defn pandascore-finished-fake
  [url page-number date-range]
  (-> (io/resource "fixtures/pandascore-finished-response.edn")
      slurp
      edn/read-string))

(defn abios-auth-token-fake
  []
  (-> (io/resource "fixtures/abios/access-token-response.edn")
      slurp
      edn/read-string))
