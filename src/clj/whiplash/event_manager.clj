(ns whiplash.event-manager
  (:require [clojure.core.async :as async]
            [mount.core :as mount]
            [whiplash.integrations.twitch :as twitch]
            [whiplash.db.core :as db]
            [cognitect.anomalies :as anom]
            [clojure.set :as set]
            [datomic.client.api :as d]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn events-go-live
  [live-streamers live-whiplash-channels]
  (let [channel-ids-to-go-live (set/difference
                                 (into #{} (map (comp string/lower-case :user_name) live-streamers))
                                 (into #{} (map :event/channel-id live-whiplash-channels)))]
    (mapv
      (fn [channel-id]
        (log/infof "attempting to start event for %s" channel-id)
        (db/create-event {:title      (->> live-streamers
                                           (filter (fn [{:keys [user_name]}]
                                                     (= channel-id (string/lower-case user_name))))
                                           first
                                           :title)
                          :channel-id channel-id
                          :source     :event.stream-source/twitch
                          :event/auto-run :event.auto-run/csgo}))
      channel-ids-to-go-live)))

(defn events-go-offline
  [db live-streamers live-whiplash-channels]
  (let [channel-ids-to-go-offline (set/difference
                                    (into #{} (map :event/channel-id live-whiplash-channels))
                                    (into #{} (map (comp string/lower-case :user_name) live-streamers)))]
    (mapv
      (fn [chan-id]
        (log/infof "attempting to end event for %s" chan-id)
        (let [event-eid (->> live-whiplash-channels
                             (filter (fn [{:keys [event/channel-id]}]
                                       (= chan-id channel-id)))
                             first
                             :db/id)
              ;; In case we never got a game state event to call a proposition but we're ending the event
              props-to-cancel (db/find-running-prop-bets {:db db :db/id event-eid})]
          ;;TODO: keep an eye out on this for its performance, makes a lot of blocking calls in a loop
          (when-not (empty? props-to-cancel)
            (mapv
              (fn [prop-eid]
                (db/cancel-proposition-and-return-cash {:proposition {:db/id prop-eid}
                                                        :db          db}))
              props-to-cancel))
          (db/end-event event-eid)))
      channel-ids-to-go-offline)))

(defn maybe-start-or-stop-csgo-events
  []
  (let [db (d/db (:conn db/datomic-cloud))
        events-chan (db/async-pull-live-csgo-twitch-events {:db      db
                                                            :attrs [:db/id
                                                                    :event/start-time
                                                                    :event/running?
                                                                    :event/channel-id
                                                                    :event/title
                                                                    :event/auto-run
                                                                    {:event/stream-source [:db/ident]}]})
        live-streamers (twitch/live-whiplash-csgo-streamers)
        result (async/<!! events-chan)
        live-whiplash-channels (when-not (::anom/anomaly result)
                                 (mapv db/update-ident-vals (flatten result)))]
    ;; when both network calls succeed
    (when (and (some? live-whiplash-channels)
               (some? live-streamers))
      (events-go-live live-streamers live-whiplash-channels)
      (events-go-offline db live-streamers live-whiplash-channels))))

(defn set-interval
  [f time-in-ms]
  (let [stop (async/chan)]
    (async/go-loop
      []
      (async/alt!
        (async/timeout time-in-ms)
        (do
          (async/<! (async/thread (f)))
          (recur))
        stop :stop))
    stop))

(def one-minute (* 1000 60))

(mount/defstate event-manager
                :start
                (do
                  (log/info "starting event manager")
                  #_(set-interval maybe-start-or-stop-csgo-events one-minute))

                :stop
                (when event-manager
                  (async/close! event-manager)))
