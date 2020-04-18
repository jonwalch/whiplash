(ns whiplash.routes.services.proposition
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]))

(defn admin-create-proposition
  [{:keys [body-params] :as req}]
  (let [{:keys [text end-betting-secs]} body-params
        ongoing-event (db/find-ongoing-event)
        ongoing-prop (db/find-ongoing-proposition)]
    (cond
      (nil? ongoing-event)
      (method-not-allowed {:message "Cannot create prop bet, no ongoing event"})

      ;; TODO more validation of text
      (empty? text)
      (method-not-allowed {:message "An empty proposition is not allowed."})

      (some? ongoing-prop)
      (method-not-allowed {:message "Cannot create prop bet, ongoing proposition exists"})

      :else
      (do
        (db/create-proposition {:text   text
                                :event-eid ongoing-event
                                :end-betting-secs end-betting-secs})
        (ok {})))))

;; TODO: remove separate query for event, can do all previous pull in one query instead of 2
(defn get-current-proposition
  [req]
  (let [prop-fields-to-pull '[:proposition/start-time
                              :proposition/text
                              :proposition/running?
                              :proposition/betting-end-time]
        db (d/db (:conn db/datomic-cloud))
        ongoing-event (db/find-ongoing-event db)
        ongoing-prop (when ongoing-event
                       (db/pull-ongoing-proposition {:db db
                                                     :attrs prop-fields-to-pull}))
        previous-prop (when ongoing-event
                        (db/pull-previous-proposition {:db db
                                                       :attrs (conj prop-fields-to-pull :proposition/result?)
                                                       :event-eid ongoing-event}))]
    (if (or ongoing-prop previous-prop)
      (ok {:current-prop  (if ongoing-prop ongoing-prop {})
           :previous-prop (if previous-prop previous-prop {})})
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
  (let [{:keys [result]} body-params
        db (d/db (:conn db/datomic-cloud))]
    (if-let [prop (db/pull-ongoing-proposition {:db db
                                                :attrs [:proposition/betting-end-time
                                                        :db/id]})]
      (do (db/end-proposition {:result?  result
                               :proposition prop
                               :db db})
          (ok {}))
      (method-not-allowed {:message "No ongoing proposition"}))))
