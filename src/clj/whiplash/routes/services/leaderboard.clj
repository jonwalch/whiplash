(ns whiplash.routes.services.leaderboard
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [whiplash.time :as time]))

;; TODO maybe cache this every 10 minutes or so if it takes too long
(defn weekly-leaderboard
  [{:keys [body-params] :as req}]
  (let [weekly-leaderboard (->> (db/find-this-week-leaderboard-calc (time/to-date (time/last-monday)))
                                (group-by (fn [guess]
                                            (-> guess :user/_guesses :user/screen-name)))
                                (map (fn [[k v]]
                                       (hash-map :screen_name k
                                                 :score (->> v
                                                                  (map :guess/score)
                                                                  (apply +)))))
                                (sort-by :score #(compare %2 %1))
                                vec)]
    (ok weekly-leaderboard)))
