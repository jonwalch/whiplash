(ns whiplash.routes.services.proposition
  (:require  [ring.util.http-response :refer :all]
             [whiplash.db.core :as db]
             [datomic.client.api :as d]))

(defn admin-create-prop-bet
  [{:keys [body-params] :as req}]
  ;; TODO validation of text
  (let [{:keys [text]} body-params
        ongoing-event (db/find-ongoing-event)
        ongoing-prop-bet (db/find-ongoing-prop-bet)]
    (cond
      (nil? ongoing-event)
      (method-not-allowed {:message "Cannot create prop bet, no ongoing event"})

      (some? ongoing-prop-bet)
      (method-not-allowed {:message "Cannot create prop bet, ongoing prop bet exists"})

      :else
      (do
        (db/create-prop-bet {:text text
                             :event-eid ongoing-event})
        (ok {})))))

(defn get-current-prop-bet
  [req]
  (if-let [prop-bet (db/find-ongoing-prop-bet)]
    {:status  200
     :headers {"Cache-Control" "max-age=3"}
     :body    (d/pull (d/db (:conn db/datomic-cloud))
                      '[:proposition/start-time
                        :proposition/text
                        :proposition/running?]
                      prop-bet)}
    {:status 404
     :headers {"Cache-Control" "max-age=3"}
     :body {}}))

(defn end-current-prop-bet
  [{:keys [body-params] :as req}]
  (let [{:keys [result]} body-params]
    (if-let [prop-bet (db/find-ongoing-prop-bet)]
      (do (db/end-prop-bet {:result?     result
                            :prop-bet-id prop-bet})
          (ok {}))
      (method-not-allowed {:message "No ongoing prop bet"}))))
