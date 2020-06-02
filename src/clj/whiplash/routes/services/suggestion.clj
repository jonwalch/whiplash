(ns whiplash.routes.services.suggestion
  (:require [whiplash.db.core :as db]
            [ring.util.http-response :refer :all]
            [datomic.client.api :as d]
            [clj-uuid :as uuid]))

(defn get-suggestions
  [{:keys [params] :as req}]
  (let [db (d/db (:conn db/datomic-cloud))
        suggestions (db/pull-undismissed-suggestions-for-ongoing-event
                      {:db    db
                       :attrs [:suggestion/text
                               :suggestion/submission-time
                               :suggestion/uuid
                               {:suggestion/user [:user/name]}]})]
    (if-not (empty? suggestions)
      (ok (sort-by :suggestion/submission-time
                   #(compare %2 %1)
                   suggestions))
      (no-content))))

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
