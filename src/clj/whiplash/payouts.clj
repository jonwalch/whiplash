(ns whiplash.payouts
  (:require [clojure.tools.logging :as log]))

(defn game-bet-totals
  [bets group-by-key]
  (let [result
        (->> bets
             (group-by group-by-key)
             (map (fn [[team-id bets]]
                    (hash-map team-id
                              (hash-map :bet/total
                                        (apply + (map :bet/amount bets))))))
             (apply conj))]
    (cond
      (and (nil? (get result true))
           (some? (get result false)))
      (merge result {true {:bet/total 0N}})

      (and (some? (get result true))
           (nil? (get result false)))
      (merge result {false {:bet/total 0N}})

      :else
      result)))

(defn team-odds
  [bet-stats]
  (let [total-bet-for-game (apply +
                                  (map (fn [[k v]]
                                         (:bet/total v))
                                       bet-stats))]
    (->> bet-stats
         (map (fn [[k {:keys [bet/total] :as v}]]
                (hash-map k (assoc v :bet/odds
                                     (double
                                       (if (not= 0N total)  ;; 0N means no bets on that side
                                         (/ total-bet-for-game total)
                                         total-bet-for-game))))))
         (apply conj))))

(defn payout-for-bet
  [{:keys [bet-stats bet/amount team/id team/winner]}]
  (when-not (nil? winner)
    (let [payout (double (* amount
                            (or (-> bet-stats
                                    (get winner)
                                    :bet/odds)
                                ;; This happens when no one bet for the other team and there are no odds
                                0.0)))
          floored-payout (Math/floor payout)]
      ;;TODO save this casino take somewhere in the DB
      (when (< 0 (- payout floored-payout))
        (log/info (format "Casino floored payout take %s dollars" (- payout floored-payout))))
      (if (= winner id)
        (bigint floored-payout)
        0N))))
