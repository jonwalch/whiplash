(ns whiplash.routes.services.proposition
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]
            [whiplash.time :as time]
            [clojure.tools.logging :as log]
            [whiplash.constants :as constants]))

(defn admin-create-proposition
  [{:keys [body-params path-params] :as req}]
  ;; TODO: input validation end-betting-secs must be greater than n
  (let [{:keys [text end-betting-secs]} body-params
        {:keys [channel-id]} path-params
        {:keys [event current-prop]} (db/pull-event-info
                                       {:attrs [:db/id
                                                {:event/propositions
                                                 '[:db/id
                                                   :proposition/running?]}]
                                        :event/channel-id channel-id})]
    (cond
      (nil? event)
      (method-not-allowed {:message "Cannot create prop bet, no ongoing event"})

      ;; TODO more validation of text
      (empty? text)
      (method-not-allowed {:message "An empty proposition is not allowed."})

      (some? current-prop)
      (method-not-allowed {:message "Cannot create prop bet, ongoing proposition exists"})

      :else
      (do
        (db/create-proposition {:text             text
                                :event-eid        (:db/id event)
                                :end-betting-secs end-betting-secs})
        (ok {})))))

(defn get-current-proposition
  [{:keys [path-params] :as req}]
  (let [{:keys [channel-id]} path-params
        {:keys [current-prop previous-prop]} (db/pull-event-info
                                               {:attrs [:db/id
                                                        {:event/propositions
                                                         '[:proposition/start-time
                                                           :proposition/text
                                                           :proposition/running?
                                                           :proposition/betting-end-time
                                                           {:proposition/result [:db/ident]}]}]
                                                :event/channel-id channel-id})]
    (if (or current-prop previous-prop)
      (ok {:current-prop  (or current-prop {})
           :previous-prop (or previous-prop {})})
      (no-content))))

(defn end-current-proposition
  [{:keys [body-params path-params] :as req}]
  (let [{:keys [result]} body-params
        {:keys [channel-id]} path-params
        db (d/db (:conn db/datomic-cloud))
        {:keys [current-prop]} (db/pull-event-info
                                       {:attrs [:db/id
                                                :event/channel-id
                                                {:event/propositions
                                                 '[:db/id
                                                   :proposition/start-time
                                                   :proposition/text
                                                   :proposition/running?
                                                   :proposition/betting-end-time
                                                   {:proposition/result [:db/ident]}]}]
                                        :event/channel-id channel-id})]
    (cond
      (not (contains? #{"true" "false" "cancel"} result))
      (method-not-allowed {:message "Invalid parameter"})

      current-prop
      (let [{:keys [db-after] :as tx-result} (db/end-betting-for-proposition current-prop)]
        (if-not (= "cancel" result)
          (db/payouts-for-proposition {:result?     (boolean (Boolean/valueOf result))
                                       :proposition current-prop
                                       :db          db-after})
          (db/cancel-proposition-and-return-cash {:proposition current-prop
                                                  :db          db-after}))
        (ok {}))

      :else
      (method-not-allowed {:message "No ongoing proposition"}))))

;; TODO move this to db core
(defn- flip-outcome
  [db {:keys [db/id proposition/result] :as previous-prop}]
  (assert (or (= result :proposition.result/true)
              (= result :proposition.result/false)))
  (let [result? (= result :proposition.result/true)
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
  [{:keys [path-params]}]
  (let [{:keys [channel-id]} path-params
        db (d/db (:conn db/datomic-cloud))
        {:keys [previous-prop]} (db/pull-event-info
                                  {:attrs            [:db/id
                                                      :event/channel-id
                                                      {:event/propositions
                                                       '[:db/id
                                                         :proposition/start-time
                                                         :proposition/text
                                                         :proposition/running?
                                                         :proposition/betting-end-time
                                                         {:proposition/result [:db/ident]}]}]
                                   :event/channel-id channel-id})
        prev-result (:proposition/result previous-prop)]
    (if (or (= :proposition.result/true prev-result)
            (= :proposition.result/false prev-result))
      (do (flip-outcome db previous-prop)
          (ok {}))
      (method-not-allowed {:message "Cannot flip prev prop. Either there isnt one or it was cancelled"}))))
