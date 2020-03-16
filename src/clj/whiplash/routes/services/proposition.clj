(ns whiplash.routes.services.proposition
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]))

(defn admin-create-proposition
  [{:keys [body-params] :as req}]
  ;; TODO validation of text
  (let [{:keys [text end-betting-secs]} body-params
        ongoing-event (db/find-ongoing-event)
        ongoing-prop (db/find-ongoing-proposition)]
    (cond
      (nil? ongoing-event)
      (method-not-allowed {:message "Cannot create prop bet, no ongoing event"})

      (some? ongoing-prop)
      (method-not-allowed {:message "Cannot create prop bet, ongoing proposition exists"})

      :else
      (do
        (db/create-proposition {:text   text
                                :event-eid ongoing-event
                                :end-betting-secs end-betting-secs})
        (ok {})))))

(defn get-current-proposition
  [req]
  (let [db (d/db (:conn db/datomic-cloud))
        ongoing-event (db/find-ongoing-event db)
        ongoing-prop (when ongoing-event
                       (db/find-ongoing-proposition db))
        previous-prop (when ongoing-event
                        (db/find-previous-proposition {:db db
                                                       :event-eid ongoing-event}))
        prop-fields-to-pull '[:proposition/start-time
                              :proposition/text
                              :proposition/running?
                              :proposition/betting-end-time]]
    (if (or ongoing-prop previous-prop)
      (ok {:current-prop  (if current-prop
                            (d/pull db prop-fields-to-pull ongoing-prop)
                            {})
           :previous-prop (if previous-prop
                            (d/pull db
                                    (conj prop-fields-to-pull :proposition/result?)
                                    previous-prop)
                            {})})
      (not-found {}))))

#_(defn end-betting-for-prop
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
