(ns whiplash.routes.services.leaderboard
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]
            [whiplash.payouts :as payouts]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [java-time :as jtime]
            [whiplash.time :as time]))

(defn all-time-top-ten
  [{:keys [params] :as req}]
  (ok (db/find-top-n 25)))

(defn event-score-leaderboard
  [{:keys [params path-params]}]
  (let [{:keys [channel-id]} path-params
        channel-id (string/lower-case channel-id)
        db (d/db (:conn db/datomic-cloud))
        ;; TODO: pull current and last n events in 1 query
        event-db-id (:db/id
                      (db/pull-ongoing-event {:db               db
                                              :attrs            [:db/id]
                                              :event/channel-id channel-id}))
        last-event-db-id (when-not event-db-id
                           (db/find-last-event {:db db :event/channel-id channel-id}))
        bets (when (or event-db-id
                       last-event-db-id)
               (db/find-all-user-bets-for-event {:db       db
                                                 :event-id (or event-db-id
                                                               last-event-db-id)}))]
    (if bets
      (ok (let [transformed-bets (->> bets
                                      :event/propositions
                                      (mapcat :bet/_proposition)
                                      (map (fn [bet]
                                             (-> bet
                                                 (assoc :user/name (get-in bet [:user/_prop-bets :user/name]))
                                                 (assoc :user/status (get-in bet [:user/_prop-bets :user/status :db/ident]))
                                                 (dissoc :user/_prop-bets)))))]
            (->> transformed-bets
                 (filter #(and (not= :user.status/unauth (:user/status %))
                               (not= :user.status/twitch-ext-unauth (:user/status %))))
                 (group-by :user/name)
                 (map (fn [[user bets]]
                        {:user_name user
                         :score     (apply +
                                           0
                                           (keep (fn [{:keys [bet/amount bet/payout]}]
                                                   (when (number? payout)
                                                     (- payout amount)))
                                                 bets))}))
                 (sort-by :score #(compare %2 %1)))))
      {:status 204})))

;; clean up this monstrosity
(defn- fake-odds-if-one-sided
  [bet-stats betting-end-time]
  (let [true-side (get bet-stats true)
        false-side (get bet-stats false)]
    (cond
      (jtime/before? (time/now)
                     (time/date-to-zdt betting-end-time))
      bet-stats

      (= 1.0 (:bet/odds true-side))                          ;; only bettors on true
      {true  (assoc true-side :bet/odds 2.0)
       false {:bet/total (* 2 (:bet/total true-side))
              :bet/odds  1.0}}

      (= 1.0 (:bet/odds false-side))                        ;; only bettors on false
      {true  {:bet/total (* 2 (:bet/total false-side))
              :bet/odds  1.0}
       false (assoc false-side :bet/odds 2.0)}

      :else                                                 ;; bettors on both
      bet-stats)))

(defonce ^:private empty-bets  {:true  {:bets  []
                                        :odds  1.00
                                        :total 0}
                                :false {:bets  []
                                        :odds  1.00
                                        :total 0}})

(defn get-prop-bets
  [{:keys [path-params]}]
  (let [{:keys [channel-id]} path-params
        channel-id (string/lower-case channel-id)
        db (d/db (:conn db/datomic-cloud))
        {:keys [current-prop]} (db/pull-event-info
                                 {:db db
                                  :attrs [{:event/propositions
                                           '[:db/id
                                             :proposition/start-time
                                             :proposition/text
                                             :proposition/running?
                                             :proposition/betting-end-time
                                             {:proposition/result [:db/ident]}]}]
                                  :event/channel-id channel-id})
        current-bets (db/find-all-user-bets-for-running-proposition {:db db :event/channel-id channel-id})]
    (if (seq current-bets)
      (let [current-bets (->> current-bets
                              (map (fn [bet]
                                     (-> bet
                                         (assoc :user/name (get-in bet [:user/_prop-bets :user/name]))
                                         (dissoc :user/_prop-bets)))))
            total-amounts-and-odds (-> current-bets
                                       (payouts/game-bet-totals :bet/projected-result?)
                                       (payouts/team-odds)
                                       (fake-odds-if-one-sided (:proposition/betting-end-time current-prop)))
            grouped-bets (group-by :bet/projected-result? current-bets)
            add-other-side (cond
                             (and (nil? (get grouped-bets true))
                                  (some? (get grouped-bets false)))
                             (assoc grouped-bets true [])

                             (and (some? (get grouped-bets true))
                                  (nil? (get grouped-bets false)))
                             (assoc grouped-bets false [])

                             :else
                             grouped-bets)]
        (ok (or (->> add-other-side
                     (map (fn [[result bets]]
                            (let [{:keys [bet/total bet/odds]} (get total-amounts-and-odds result)]
                              {result {:bets (sort-by :bet/amount #(compare %2 %1)
                                                      (map #(dissoc % :bet/projected-result?) bets))
                                       :total total
                                       :odds  odds}})))
                     (apply merge))
                empty-bets)))
      (ok empty-bets))))
