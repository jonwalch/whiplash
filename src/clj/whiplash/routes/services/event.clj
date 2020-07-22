(ns whiplash.routes.services.event
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [datomic.client.api :as d]
            [whiplash.time :as time]
            [clojure.tools.logging :as log]
            [selmer.parser :as parser]))

(defn create-event
  [{:keys [body-params] :as req}]
  ;; TODO: don't require channel-id for cnn-unauth
  (let [{:keys [title channel-id source]} body-params
        source-valid? (contains? #{"twitch" "youtube" "cnn-unauth" "none"} source)]
    (cond
      (some empty? [title channel-id source])
      (bad-request {:message "No args can be empty."})

      (some? (db/find-ongoing-event))
      (method-not-allowed {:message "Cannot create event, one is already ongoing"})

      (not source-valid?)
      (bad-request {:message "Source is incorrect."})

      :else
      (do
        (db/create-event {:title       title
                          :channel-id channel-id
                          :source (case source
                                    "twitch" :event.stream-source/twitch
                                    "youtube" :event.stream-source/youtube
                                    "cnn-unauth" :event.stream-source/cnn-unauth
                                    "none" :event.stream-source/none)})
        (ok {})))))

(defn get-current-event
  [{:keys [body-params headers] :as req}]
  ;; TODO make this version check a middleware function that is applied on a per route basis
  (let [client-bundle-version (get headers "client-version")
        server-bundle-version (second
                                (re-find #"\/dist\/app\.(.*)\.js"
                                         (parser/render-file
                                           "index.html"
                                           {:page "index.html"})))]
    (if (and (and (not (nil? client-bundle-version))
                  (not (nil? server-bundle-version)))
             (not= client-bundle-version server-bundle-version))
      (reset-content)
      (let [db (d/db (:conn db/datomic-cloud))
            event (db/pull-ongoing-event {:db    db
                                          :attrs [:event/start-time
                                                  :event/running?
                                                  :event/channel-id
                                                  :event/title
                                                  {:event/stream-source [:db/ident]}]})
            next-event-time (when-not event
                              (db/pull-next-event-time {:db    db
                                                        :attrs [:whiplash/next-event-time]}))]
        (cond
          (some? event)
          (ok event)

          (and (some? next-event-time)
               (java-time/after? (time/date-to-zdt (:whiplash/next-event-time next-event-time))
                                 (time/now)))
          (ok next-event-time)

          :else
          (no-content))))))

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
          (db/reset-twitch-user-cash)
          (ok {})))))

(defn create-countdown
  [{:keys [body-params] :as req}]
  (let [{:keys [ts]} body-params
        parsed-date (try (time/timestamp-to-zdt ts)
                         (catch Throwable _ nil))
        existing-countdown (when parsed-date
                             (db/pull-next-event-time {:attrs
                                                       [:db/id :whiplash/next-event-time]}))]
    (cond
      (nil? parsed-date)
      (bad-request {:message "Couldn't parse date ISO 8601 string"})

      (java-time/before? parsed-date (time/now))
      (bad-request {:message "Date must be in the future"})

      :else
      (do
        (d/transact (:conn db/datomic-cloud)
                    {:tx-data [(merge
                                 {:whiplash/next-event-time (time/to-date parsed-date)}
                                 (when existing-countdown
                                   {:db/id (:db/id existing-countdown)}))]})
        (ok {})))))
