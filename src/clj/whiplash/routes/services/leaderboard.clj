(ns whiplash.routes.services.leaderboard
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [whiplash.time :as time]
            [datomic.client.api :as d]
            [whiplash.payouts :as payouts]
            [clojure.set :as set]
            [clojure.tools.logging :as log]))

(defn all-time-top-ten
  [{:keys [params] :as req}]
  (ok (db/find-top-ten)))

(defn weekly-leaderboard
  [{:keys [params] :as req}]
  (let [weekly-leaderboard (->> (db/find-this-week-payout-leaderboard (time/to-date (time/last-monday)))
                                (group-by :user/name)
                                (map (fn [[k v]]
                                       (hash-map :user_name k
                                                 :payout (->> v
                                                              (map :bet/payout)
                                                              (apply +)))))
                                (sort-by :payout #(compare %2 %1)))]
    (ok weekly-leaderboard)))

(defn weekly-prop-bet-leaderboard
  [{:keys [params] :as req}]
  (let [weekly-leaderboard (->> (db/find-this-week-prop-bet-payout-leaderboard (time/to-date (time/last-monday)))
                                (group-by :user/name)
                                (map (fn [[k v]]
                                       (hash-map :user_name k
                                                 :payout (->> v
                                                              (map :bet/payout)
                                                              (apply +)))))
                                (sort-by :payout #(compare %2 %1)))]
    (ok weekly-leaderboard)))

(defn event-score-leaderboard
  [{:keys [params] :as req}]
  (let [db (d/db (:conn db/datomic-cloud))
        ongoing-event (db/find-ongoing-event db)
        last-event (db/find-last-event db)
        bets (when (or ongoing-event
                       last-event)
               (ffirst
                 (db/find-all-user-bets-for-event {:db db
                                                   :event-id (or ongoing-event
                                                                 last-event)})))]
    (if bets
      (ok
        (let [transformed-bets (->> bets
                                    :event/propositions
                                    (mapcat :bet/_proposition)
                                    (map (fn [bet]
                                           (-> bet
                                               (assoc :user/name (get-in bet [:user/_prop-bets :user/name]))
                                               (dissoc :user/_prop-bets)))))]
          (->> transformed-bets
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
      (not-found []))))

(defn get-bets
  [{:keys [params] :as req}]
  (let [{:keys [game_id match_id]} params
        ;; TODO figure out why this isnt getting casted by middleware
        game-id (Integer/parseInt game_id)
        match-id (Integer/parseInt match_id)
        unprocessed-bets (db/find-all-unprocessed-bets-for-game (d/db (:conn db/datomic-cloud))
                                                                {:match-id match-id
                                                                 :game-id  game-id})
        total-amounts-and-odds (-> unprocessed-bets
                                   (payouts/game-bet-totals :team/name)
                                   (payouts/team-odds))]
    (ok (or (->> unprocessed-bets
                 (group-by :team/name)
                 (map (fn [[team-name bets]]
                        (let [{:keys [bet/total bet/odds]} (get total-amounts-and-odds team-name)]
                          {team-name {:bets (sort-by :bet/amount
                                                     #(compare %2 %1)
                                                     (->> bets
                                                          (group-by :user/name)
                                                          (mapv (fn [[user-name bets]]
                                                                  {:user/name user-name
                                                                   :bet/amount (apply +
                                                                                      (map :bet/amount bets))}))))
                              :total total
                              :odds  odds}})))
                 (apply merge))
            {}))))

(defn get-prop-bets
  [{:keys [params] :as req}]
  (let [db (d/db (:conn db/datomic-cloud))
        current-proposition (db/find-ongoing-proposition db)]
    (if current-proposition
      (let [current-bets (->> (db/find-all-user-bets-for-proposition {:db db
                                                                      :prop-bet-id current-proposition})
                              (apply concat)
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
                              {result {:bets  (sort-by :bet/amount
                                                       #(compare %2 %1)
                                                       (->> bets
                                                            (group-by :user/name)
                                                            (mapv (fn [[user-name bets]]
                                                                    {:user/name  user-name
                                                                     :bet/amount (apply +
                                                                                        (map :bet/amount bets))}))))
                                       :total total
                                       :odds  odds}})))
                     (apply merge))
                {})))
      (not-found {:message "no ongoing prop bet"}))))
