(ns whiplash.routes.services.proposition
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]
            [whiplash.time :as time]
            [clojure.tools.logging :as log]
            [whiplash.constants :as constants]))

(defn admin-create-proposition
  [{:keys [body-params] :as req}]
  ;; TODO: input validation end-betting-secs must be greater than n
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

(defn get-current-proposition
  [req]
  (let [{:keys [current-prop previous-prop]} (db/pull-event-info
                                               {:attrs [:db/id
                                                        {:event/propositions
                                                         '[:proposition/start-time
                                                           :proposition/text
                                                           :proposition/running?
                                                           :proposition/betting-end-time
                                                           {:proposition/result [:db/ident]}]}]})]
    (if (or current-prop previous-prop)
      (ok {:current-prop  (or current-prop {})
           :previous-prop (or previous-prop {})})
      (no-content))))

(defn end-current-proposition
  [{:keys [body-params] :as req}]
  (let [{:keys [result]} body-params
        db (d/db (:conn db/datomic-cloud))
        prop (db/pull-ongoing-proposition {:db db
                                           :attrs [:proposition/betting-end-time
                                                   :db/id]})]
    (cond
      (not (contains? #{"true" "false" "cancel"} result))
      (method-not-allowed {:message "Invalid parameter"})

      prop
      (let [{:keys [db-after] :as tx-result} (db/end-betting-for-proposition prop)]
        (if-not (= "cancel" result)
          (db/payouts-for-proposition {:result?     (boolean (Boolean/valueOf result))
                                       :proposition prop
                                       :db          db-after})
          (db/cancel-proposition-and-return-cash {:proposition prop
                                                  :db          db-after}))
        (ok {}))

      :else
      (method-not-allowed {:message "No ongoing proposition"}))))

;; TODO move this to db core
(defn- flip-outcome
  [db {:keys [db/id proposition/result] :as previous-prop}]
  (assert (or (= (:db/ident result) :proposition.result/true)
              (= (:db/ident result) :proposition.result/false)))
  (let [result (:db/ident result)
        result? (= result :proposition.result/true)
        user-id->total-payout (->> (db/pull-bet-payout-info
                                     {:db          db
                                      :prop-bet-id id
                                      :attrs       [:db/id :bet/amount :bet/payout :bet/projected-result?
                                                    {:user/_prop-bets [:user/cash
                                                                       :db/id
                                                                       {:user/status [:db/ident]}]}]})
                                   (filter #(= result? (:bet/projected-result? %)))
                                   db/user-id->total-pay
                                   (map (fn [[user-db-id payout-info]]
                                          {user-db-id (update payout-info :user/total-payout -)}))
                                   (apply merge))
        tx-result (d/transact (:conn db/datomic-cloud)
                              {:tx-data (db/generate-user-cash-txs {:user-id->total-payout user-id->total-payout
                                                                    :flip?                 true})})]
    (d/transact (:conn db/datomic-cloud)
                {:tx-data (db/generate-prop-payout-txs {:db          (:db-after tx-result)
                                                        :proposition previous-prop
                                                        :result?     (not result?)})})))

(defn flip-prev-prop-outcome
  [req]
  (let [db (d/db (:conn db/datomic-cloud))
        ongoing-event (db/find-ongoing-event db)
        previous-prop (when ongoing-event
                        (db/pull-previous-proposition {:db        db
                                                       :attrs     '[:db/id
                                                                    {:proposition/result [:db/ident]}
                                                                    :proposition/betting-end-time]
                                                       :event-eid ongoing-event}))
        prev-result (-> previous-prop :proposition/result :db/ident)
        result-valid? (or (= :proposition.result/true prev-result)
                          (= :proposition.result/false prev-result))]
    (if result-valid?
      (do (flip-outcome db previous-prop)
          (ok {}))
      (method-not-allowed {:message "Cannot flip prev prop. Either there isnt one or it was cancelled"}))))
