(ns whiplash.routes.services.event
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]))

(defn create-event
  [{:keys [body-params] :as req}]
  (let [{:keys [title twitch-user]} body-params]
    (if (nil? (db/find-ongoing-event))
      (do
        (db/create-event {:title       title
                          :twitch-user twitch-user})
        (ok))
      (method-not-allowed {:message "Cannot create event, one is already ongoing"}))))

(defn get-current-event
  [{:keys [body-params] :as req}]
  (if-let [event (db/find-ongoing-event)]
    (ok (d/pull (d/db (:conn db/datomic-cloud))
                '[:event/start-time
                  :event/running?
                  :event/twitch-user
                  :event/title]
                event))
    (not-found)))

(defn end-current-event
  [{:keys [body-params] :as req}]
  ;; TODO: do not allow ending if there's an open prop bet
  (if-let [event (db/find-ongoing-event)]
    (do (db/end-event event)
        (ok))
    (method-not-allowed {:message "No ongoing event"})))
