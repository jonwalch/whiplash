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

(defn app-fixtures
  []
  (use-fixtures
    :once
    (fn [f]
      (mount/start #'whiplash.config/env
                   #'whiplash.handler/app-routes
                   #'whiplash.routes.services.stream/cached-streams)
      (f)
      (mount/stop #'whiplash.config/env
                  #'whiplash.handler/app-routes
                  #'whiplash.routes.services.stream/cached-streams))))

(defn db-fixtures
  []
  (use-fixtures
    :each
    (fn [f]
      (reset! test-state {})
      (mount/start #'whiplash.db.core/conn)
      (f)
      (mount/stop #'whiplash.db.core/conn))))

(defn twitch-view-fake
  [matches]
  (->> matches
       (map
         #(hash-map (:twitch/username %) 100))
       (apply conj)))

(defn internal-send-verification-email-fake
  [{:keys [user/email body subject] :as params}]
  (swap! test-state (fn [{:keys [emails] :as val}]
                      (assoc val :emails (conj emails params)))))

(defn test-wrap-base [handler]
  (-> ((:middleware defaults) handler)
      middleware/wrap-auth
      (wrap-defaults
        (-> site-defaults
            ;; This is commented out in the real version
            (assoc-in [:security :anti-forgery] false)
            (assoc-in  [:session :store] (ttl-memory-store (* 60 30))))) ;;seconds
      middleware/wrap-internal-error))

(defn test-app []
  "This version has CSRF disabled for testing purposes."
  (test-wrap-base #'whiplash.handler/app-routes))

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
