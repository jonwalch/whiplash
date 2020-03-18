(ns whiplash.routes.services.suggestion
  (:require [whiplash.db.core :as db]
            [ring.util.http-response :refer :all]
            [datomic.client.api :as d]
            [clj-uuid :as uuid]))

;; TODO refactor out all `pull`s in `map`s
(defn get-suggestions
  [{:keys [params] :as req}]
  (let [db (d/db (:conn db/datomic-cloud))
        ongoing-event (db/find-ongoing-event db)]
    (if (nil? ongoing-event)
      (not-found [])
      (->> (d/pull db '[:event/suggestions] ongoing-event)
           :event/suggestions
           (filter #(false? (:suggestion/dismissed? %)))
           (map (fn [{:keys [suggestion/user] :as suggestion}]
                  (assoc suggestion :user/name (:user/name
                                                 (d/pull db
                                                         '[:user/name]
                                                         (:db/id user))))))
           (map #(dissoc % :db/id :suggestion/user
                         :suggestion/dismissed?))
           (sort-by :suggestion/submission-time #(compare %2 %1))
           (ok)))))

(defn dismiss-suggestions
  [{:keys [body-params] :as req}]
  (let [input-uuids (:suggestions body-params)
        valid-uuids? (every? uuid/uuid-string? input-uuids)
        db (d/db (:conn db/datomic-cloud))]
    (if valid-uuids?
      (let [suggestion-eids (db/find-target-suggestions
                              {:uuids (mapv uuid/as-uuid input-uuids)
                               :db db})]
        (if-not (empty? suggestion-eids)
          (do (db/dismiss-suggestions suggestion-eids)
              (ok {}))
          (not-found {:message "No suggestions found for input"})))
      (bad-request {:message "Invalid input"}))))
