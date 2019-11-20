(ns whiplash.routes.services.leaderboard
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [whiplash.time :as time]
            [datomic.client.api :as d]
            [whiplash.guess-processor :as guess-processor]))

;; TODO maybe cache this every 10 minutes or so if it takes too long
(defn weekly-leaderboard
  [{:keys [params] :as req}]
  (let [weekly-leaderboard (->> (db/find-this-week-leaderboard (time/to-date (time/last-monday)))
                                (group-by :user/name)
                                (map (fn [[k v]]
                                       (hash-map :user_name k
                                                 :payout (->> v
                                                              (map :bet/payout)
                                                              (apply +)))))
                                (sort-by :payout #(compare %2 %1))
                                vec)]
    (ok weekly-leaderboard)))

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
                                   (guess-processor/game-bet-stats :team/name)
                                   (guess-processor/team-odds))]
    (ok (or (->> unprocessed-bets
                 (group-by :team/name)
                 (map (fn [[k v]]
                        (let [{:keys [bet/total bet/odds]} (get total-amounts-and-odds k)]
                          {k {:bets (sort-by :bet/amount #(compare %2 %1) v)
                              :total total
                              :odds odds}})))
                 (apply merge))
            {}))))
