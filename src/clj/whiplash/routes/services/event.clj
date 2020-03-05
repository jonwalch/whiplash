(ns whiplash.routes.services.event
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]))

(defn create-event
  [{:keys [body-params] :as req}]
  ;; TODO validation of twitch and twitch_user
  (let [{:keys [title twitch_user]} body-params]
    (if (nil? (db/find-ongoing-event))
      (do
        (db/create-event {:title       title
                          :twitch-user twitch_user})
        (ok {}))
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
    (not-found {})))

(defn end-current-event
  [{:keys [body-params] :as req}]
  (let [event (db/find-ongoing-event)
        prop-bet (db/find-ongoing-proposition)]
    (cond
      (some? prop-bet)
      (method-not-allowed {:message "Ongoing prop bet, you must end it before ending the event"})

      (nil? event)
      (method-not-allowed {:message "No ongoing event"})

      :else
      (do (db/end-event event)
          (ok {})))))
