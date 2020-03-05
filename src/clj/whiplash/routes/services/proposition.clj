(ns whiplash.routes.services.proposition
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]
            [clojure.tools.logging :as log]))

(defn admin-create-proposition
  [{:keys [body-params] :as req}]
  ;; TODO validation of text
  (let [{:keys [text]} body-params
        ongoing-event (db/find-ongoing-event)
        ongoing-prop (db/find-ongoing-proposition)]
    (cond
      (nil? ongoing-event)
      (method-not-allowed {:message "Cannot create prop bet, no ongoing event"})

      (some? ongoing-prop)
      (method-not-allowed {:message "Cannot create prop bet, ongoing proposition exists"})

      :else
      (do
        (db/create-prop-bet {:text text
                             :event-eid ongoing-event})
        (ok {})))))

(defn get-current-proposition
  [req]
  (if-let [prop (db/find-ongoing-proposition)]
    (ok (d/pull (d/db (:conn db/datomic-cloud))
                '[:proposition/start-time
                  :proposition/text
                  :proposition/running?
                  :proposition/betting-end-time]
                prop))
    (not-found {})))

(defn end-betting-for-prop
  [{:keys [body-params] :as req}]
  (let [db (d/db (:conn db/datomic-cloud))
        prop (db/find-ongoing-proposition db)]
    (cond
      (nil? prop)
      (method-not-allowed {:message "No ongoing proposition"})

      (some? (:proposition/betting-end-time
               (d/pull db '[:proposition/betting-end-time] prop)))
      (method-not-allowed {:message "Already ended betting for this prop"})

      :else
      (do
        (db/end-betting-for-proposition {:proposition-eid prop})
        (ok {:message "Successfully ended betting for prop."})))))

(defn end-current-proposition
  [{:keys [body-params] :as req}]
  (let [{:keys [result]} body-params]
    (if-let [prop (db/find-ongoing-proposition)]
      (do (db/end-proposition {:result?  result
                               :prop-bet-id prop})
          (ok {}))
      (method-not-allowed {:message "No ongoing proposition"}))))
