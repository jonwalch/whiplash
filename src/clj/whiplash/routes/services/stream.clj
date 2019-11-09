(ns whiplash.routes.services.stream
  (:require [ring.util.http-response :refer :all]
            [whiplash.integrations.pandascore :as pandascore]
            [mount.core :as mount]
            [java-time :as java-time]
            [whiplash.time :as time]
            [clojure.tools.logging :as log]))

;;TODO this will not work when (count webservers) > 1
(mount/defstate cached-streams
                :start
                (atom {})

                :stop
                (atom {}))

(defn get-stream
  [{:keys [params] :as req}]
  (let [{:keys [streams/last-fetch streams/ordered-candidates]} (deref cached-streams)
        return-fn (fn [{:keys [stream cached?]}]
                    (if (nil? stream)
                      (do
                        (when-not cached?
                          (log/info "Couldn't find a stream candidate"))
                        (no-content))
                      (ok stream)))]
    (if (or (nil? last-fetch)
            (java-time/after? (time/now) (time/minutes-delta last-fetch 1)))
      (do
        (log/info "Fetching streams")
        (let [all-streams (-> :csgo
                              pandascore/get-matches
                              pandascore/sort-and-transform-stream-candidates)]
          (reset! cached-streams {:streams/last-fetch (time/now)
                                  :streams/ordered-candidates    all-streams})
          (return-fn {:stream (first all-streams)
                      :cached? false})))
      (do
        (log/info "Serving cached stream")
        (return-fn {:stream (first ordered-candidates)
                    :cached? true})))))
