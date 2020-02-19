(ns whiplash.routes.services.stream
  (:require [ring.util.http-response :refer :all]
            [whiplash.integrations.pandascore :as pandascore]
            [mount.core :as mount]
            [java-time :as java-time]
            [whiplash.time :as time]
            [clojure.tools.logging :as log]))

(mount/defstate cached-streams
                :start
                (atom {})

                :stop
                (atom {}))

;; In development, if no games are currently running you can fake it to get the UI up
;; just wrap the body of the get-stream function with this
;; (with-redefs [whiplash.integrations.pandascore/get-matches-request whiplash.test.common/pandascore-running-fake
;;               whiplash.integrations.twitch/views-per-twitch-stream whiplash.test.common/twitch-view-fake]
;; )
(defn get-stream
  [{:keys [params] :as req}]
  (let [{:keys [streams/last-fetch streams/ordered-candidates]} (deref cached-streams)
        return-fn (fn [{:keys [stream cached?]}]
                    (if (nil? stream)
                      (do
                        (when-not cached?
                          (log/debug "Couldn't find a stream candidate"))
                        (no-content))
                      (ok stream)))]
    (if (or (nil? last-fetch)
            (java-time/after? (time/now) (time/minutes-delta last-fetch 1)))
      (let [all-streams (-> :csgo
                            pandascore/get-matches
                            pandascore/sort-and-transform-stream-candidates)]
        (log/debug "Fetching streams")
        (reset! cached-streams {:streams/last-fetch         (time/now)
                                :streams/ordered-candidates all-streams})
        (return-fn {:stream  (first all-streams)
                    :cached? false}))
      (do
        (log/debug "Serving cached stream")
        (return-fn {:stream  (first ordered-candidates)
                    :cached? true})))))

;; Pull stream from DB, that was set from admin panel
(defn get-homegrown-stream
  [])
