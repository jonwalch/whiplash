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
  ;; TODO: channel-id cannot have spaces
  (let [{:keys [title channel-id source]} body-params
        source-valid? (contains? #{"twitch" "youtube" "cnn-unauth" "none"} source)]
    (cond
      (some empty? [title channel-id source])
      (bad-request {:message "No args can be empty."})

      (some? (db/pull-ongoing-event {:attrs [:db/id]
                                     :event/channel-id channel-id}))
      (method-not-allowed {:message (format "Cannot create event. Event already ongoing for %s" channel-id)})

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

;; TODO make this version check a middleware function that is applied on a per route basis
(defn outdated-client?
  [headers]
  (let [client-bundle-version (get headers "client-version")
        server-bundle-version (second
                                (re-find #"\/dist\/app\.(.*)\.js"
                                         (parser/render-file
                                           "index.html"
                                           {:page "index.html"})))]
    (and
      (and (some? client-bundle-version)
           (some? server-bundle-version))
      (not= client-bundle-version server-bundle-version))))

(defn get-all-current-events
  [{:keys [body-params headers] :as req}]
  (if (outdated-client? headers)
    (reset-content)
    (let [db (d/db (:conn db/datomic-cloud))
          events (db/pull-all-ongoing-events {:db    db
                                              :attrs [:event/start-time
                                                      :event/running?
                                                      :event/channel-id
                                                      :event/title
                                                      {:event/stream-source [:db/ident]}]})]
      (cond
        (not (empty? events))
        (ok (sort-by :event/channel-id events))

        :else
        (no-content)))))

(defn get-current-event
  [{:keys [body-params path-params headers]}]
  (let [{:keys [channel-id]} path-params]
    (if (outdated-client? headers)
      (reset-content)
      (let [db (d/db (:conn db/datomic-cloud))
            event (db/pull-ongoing-event {:db    db
                                           :attrs [:event/start-time
                                                   :event/running?
                                                   :event/channel-id
                                                   :event/title
                                                   {:event/stream-source [:db/ident]}]
                                           :event/channel-id channel-id})]
        (cond
          (not (empty? event))
          (ok event)

          :else
          (no-content))))))

(defn end-current-event
  [{:keys [body-params path-params] :as req}]
  (let [channel-id (:channel-id path-params)
        {:keys [event current-prop]} (db/pull-event-info
                                       {:attrs [:db/id
                                                :event/channel-id
                                                {:event/propositions
                                                 '[:db/id
                                                   :proposition/start-time
                                                   :proposition/text
                                                   :proposition/running?
                                                   :proposition/betting-end-time
                                                   {:proposition/result [:db/ident]}]}]
                                        :event/channel-id channel-id})]
    (cond
      (some? current-prop)
      (method-not-allowed {:message "Ongoing prop bet, you must end it before ending the event"})

      (nil? event)
      (method-not-allowed {:message (format "Event for channel-id %s not found" channel-id)})

      :else
      (do (db/end-event (:db/id event))
          (db/reset-twitch-user-cash)
          (ok {})))))

#_(defn create-countdown
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
