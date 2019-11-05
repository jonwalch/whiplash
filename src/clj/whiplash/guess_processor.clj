(ns whiplash.guess-processor
  (:require [whiplash.db.core :as db]
            [clojure.tools.logging :as log]
            [datomic.client.api :as d]
            [whiplash.integrations.pandascore :as pandascore]
            [whiplash.time :as time]
            [clojure.core.async :as async]
            [mount.core :as mount]
            [whiplash.db.core :refer [datomic-cloud]]))

(defn- pandascore-match->game-lookup
  [game]
  (->> (pandascore/get-matches game)
       (filter #(= "finished" (:status %)))
       (map :games)
       flatten
       (group-by :match_id)))

(defn process-guesses
  []
  (log/info "Processing guesses")
  (let [unprocessed-guesses (db/find-all-unprocessed-guesses) #_(mapv
                              ;;each guess is a vector with 1 element
                              (fn [guess]
                                (d/pull (d/db (:conn db/datomic-cloud))
                                       '[*]
                                        (first guess)))
                              (db/find-all-unprocessed-guesses))
        match->game-lookup (when (not-empty unprocessed-guesses)
                             (log/info (format "Found guesses to process %s" unprocessed-guesses))
                             (pandascore-match->game-lookup :csgo))
        update-txs (->> unprocessed-guesses
                        (keep
                          (fn [guess]
                            (let [games-in-match (get match->game-lookup (:match/id guess))
                                  result {:db/id                (:db/id guess)
                                          :guess/score          0
                                          :guess/processed?     true
                                          :guess/processed-time (time/to-date)}]
                              (if (some? games-in-match)
                                (keep
                                  (fn [game]
                                    ;; TODO: make sure the game types match
                                    (when (= (:id game) (:game/id guess))
                                      (if (= (get-in game [:winner :id]) (:team/id guess))
                                        (assoc result :guess/score 100)
                                        result)))
                                  games-in-match)
                                (log/info (format "Match id %s not found in finished game lookup."
                                                  (:match/id guess)))))))
                        flatten
                        vec)]
    (when (not-empty update-txs)
      (do (log/info "Transacting processed guess updates %s" update-txs)
          (d/transact (:conn db/datomic-cloud) {:tx-data update-txs})))))

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

(def one-minute (* 1000 60 1))

(mount/defstate guess-processor
                :start
                (set-interval process-guesses one-minute)

                :stop
                (when guess-processor
                  (async/close! guess-processor)))
