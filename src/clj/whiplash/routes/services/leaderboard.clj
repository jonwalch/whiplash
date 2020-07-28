(ns whiplash.routes.services.leaderboard
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]
            [whiplash.payouts :as payouts]
            [whiplash.constants :as constants]))

(defn all-time-top-ten
  [{:keys [params] :as req}]
  (ok (db/find-top-n 25)))

(defn event-score-leaderboard
  [{:keys [params] :as req}]
  (let [db (d/db (:conn db/datomic-cloud))
        ;; TODO: retrieve event with most recent start time, this greatly simplifys this logic
        ongoing-event (db/find-ongoing-event db)
        last-event (when-not ongoing-event
                     (db/find-last-event db))
        bets (when (or ongoing-event
                       last-event)
               (db/find-all-user-bets-for-event {:db       db
                                                 :event-id (or ongoing-event
                                                               last-event)}))]
    (if bets
      {:status  200
       :headers constants/CORS-GET-headers
       :body    (let [transformed-bets (->> bets
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
                       (sort-by :score #(compare %2 %1))))}
      {:status  204
       :headers constants/CORS-GET-headers})))

(defonce ^:private empty-bets  {:true  {:bets  []
                                        :odds  1.00
                                        :total 0}
                                :false {:bets  []
                                        :odds  1.00
                                        :total 0}})

(defn get-prop-bets
  [{:keys [params] :as req}]
  (let [db (d/db (:conn db/datomic-cloud))
        current-bets (apply concat (db/find-all-user-bets-for-running-proposition {:db db}))]
    (if (seq current-bets)
      (let [current-bets (->> current-bets
                              (map (fn [bet]
                                     (-> bet
                                         (assoc :user/name (get-in bet [:user/_prop-bets :user/name]))
                                         (dissoc :user/_prop-bets)))))
            total-amounts-and-odds (-> current-bets
                                       (payouts/game-bet-totals :bet/projected-result?)
                                       (payouts/team-odds))
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
