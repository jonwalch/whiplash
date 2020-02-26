(ns whiplash.guess-processor
  (:require [whiplash.db.core :as db]
            [clojure.tools.logging :as log]
            [datomic.client.api :as d]
            [whiplash.integrations.pandascore :as pandascore]
            [whiplash.time :as time]
            [clojure.core.async :as async]
            [mount.core :as mount]
            [whiplash.db.core :refer [datomic-cloud]]
            [whiplash.payouts :as payouts]
            [clojure.set :as set]))

(defn- match-and-game->winner
  [game]
  (->> (pandascore/get-matches game)
       (map :games)
       flatten
       (filter #(= "finished" (:status %)))
       (map #(hash-map (set/rename-keys (select-keys % [:match_id :id])
                                        {:match_id :match/id
                                         :id       :game/id})
                       (get-in % [:winner :id])))
       (apply merge)))

(defn process-bets
  []
  (log/debug "Processing bets")
  (let [db (d/db (:conn db/datomic-cloud))
        unprocessed-bets (group-by #(select-keys % [:match/id :game/id])
                                   (db/find-all-unprocessed-bets db))
        winner-lookup (when (not-empty unprocessed-bets)
                        (log/debug (format "Found bets to process %s" unprocessed-bets))
                        (match-and-game->winner :csgo))
        game-info->bet-info (->> unprocessed-bets
                                 (map (fn [[k bets]]
                                        (hash-map k (hash-map :bets bets
                                                              :stats (-> bets
                                                                         (payouts/game-bet-stats :team/id)
                                                                         (payouts/team-odds))))))
                                 (apply merge))
        ;; TODO: what happens if a user makes a new bet while this is running?
        ;; I *think* we'll be ok, if the cas fails, it'll try again in 10 seconds
        bets-and-users (mapcat
                         (fn [[game-info {:keys [stats bets] :as bet-info}]]
                           (keep
                             (fn [bet]
                               (when-let [user-payout (payouts/payout-for-bet
                                                        {:bet-stats    stats
                                                         :bet/amount  (:bet/amount bet)
                                                         :team/id     (:team/id bet)
                                                         :team/winner (get winner-lookup game-info)})]
                                 (let [user-eid (-> bet :user/_bets :db/id)
                                       cash (-> db
                                                (d/pull '[:user/cash] user-eid)
                                                :user/cash)]
                                   {:user/db-id         user-eid
                                    :user/cash          cash
                                    :bet/db-id          (:db/id bet)
                                    :bet/payout         (bigint user-payout)
                                    :bet/processed?     true
                                    :bet/processed-time (time/to-date)})))
                             bets))
                         game-info->bet-info)
        user-cash-txs (->> bets-and-users
                           (group-by :user/db-id)
                           (map (fn [[k v]]
                                  {k {:total-payout (apply + (map :bet/payout v))
                                      ;; these will all be the same so just take the first
                                      :current-cash (-> v first :user/cash)}}))
                           (apply merge)
                           (mapv (fn [[k {:keys [total-payout current-cash]}]]
                                   ;; If the user falls below 100, put them back up to 100
                                   [:db/cas k :user/cash current-cash (if (< (bigint 100) (+ current-cash total-payout))
                                                                        (+ current-cash total-payout)
                                                                        (bigint 100))])))
        bet-txs (mapv (fn [{:keys [bet/db-id bet/payout]}]
                        {:db/id              db-id
                         :bet/payout         payout
                         :bet/processed?     true
                         :bet/processed-time (time/to-date)})
                      bets-and-users)
        txs (into user-cash-txs
                  bet-txs)]
    (when (not-empty txs)
      (do (log/info "Transacting processed bet updates %s" txs)
          (d/transact (:conn db/datomic-cloud) {:tx-data txs})))))

(defn set-interval
  [f time-in-ms]
  (let [stop (async/chan)]
    (async/go-loop []
      (async/alt!
        (async/timeout time-in-ms)
        (do
          (async/<!
            (async/thread
              (f)))
          (recur))
        stop :stop))
    stop))

(def ten-seconds (* 1000 10))

#_(mount/defstate guess-processor
                :start
                (set-interval process-bets ten-seconds)

                :stop
                (when guess-processor
                  (async/close! guess-processor)))
