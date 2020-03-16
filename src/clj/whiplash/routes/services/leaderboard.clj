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
                                (sort-by :payout #(compare %2 %1))
                                vec)]
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
                                (sort-by :payout #(compare %2 %1))
                                vec)]
    (ok weekly-leaderboard)))

(defn event-score-leaderboard
  [{:keys [params] :as req}]
  (let [db (d/db (:conn db/datomic-cloud))
        ongoing-event (db/find-ongoing-event db)
        last-event (db/find-last-event db)
        props (when (or ongoing-event
                        last-event)
                (:event/propositions
                  (d/pull db '[:event/propositions] (or ongoing-event
                                                        last-event))))]
    (if props
      (ok
        (->> props
             (mapcat (fn [{:keys [db/id]}]
                       (:bet/_proposition
                         (d/pull db '[:bet/_proposition] id))))
             (map :db/id)
             (map (fn [id]
                    (-> db
                        (d/pull '[:bet/amount :bet/payout :user/_prop-bets] id)
                        (set/rename-keys {:user/_prop-bets :user/name})
                        (update :user/name (fn [{:keys [db/id] :as user}]
                                             (:user/name
                                               (d/pull db '[:user/name] id)))))))
             (group-by :user/name)
             (map (fn [[user bets]]
                    {:user_name user
                     :score     (apply +
                                       0
                                       (keep (fn [{:keys [bet/amount bet/payout]}]
                                              (when (number? payout)
                                                (- payout amount)))
                                            bets))}))
             (sort-by :score #(compare %2 %1))
             vec))
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
        current-prop-bet (db/find-ongoing-proposition db)]
    (if current-prop-bet
      (let [current-bets (->> (db/find-prop-bets {:db          db
                                                  :prop-bet-id current-prop-bet})
                              (map #(d/pull db '[:bet/amount :bet/projected-result? :user/_prop-bets] %))
                              (mapv (fn [bet]
                                      (-> bet
                                          (merge (d/pull db
                                                         '[:user/name]
                                                         (-> bet :user/_prop-bets :db/id)))
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
