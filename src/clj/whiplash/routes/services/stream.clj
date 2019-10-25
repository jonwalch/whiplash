(ns whiplash.routes.services.stream
  (:require [ring.util.http-response :refer :all]
            [whiplash.integrations.pandascore :as pandascore]
            [mount.core :as mount]
            [java-time :as java-time]
            [whiplash.time :as time]
            [clojure.tools.logging :as log]))

;;TODO this will not work when (count webservers) > 1
(mount/defstate ^{:on-reload :noop} cached-streams
                :start
                (atom {})

                :stop
                (atom {}))

(defn get-stream
  [{:keys [params] :as req}]
  ;; TODO check atom if too old get again
  (let [{:keys [streams/last-fetch streams/ordered-candidates]} (deref cached-streams)
        return-fn (fn [stream]
                    (if (nil? stream)
                      (do
                        (log/info "Couldn't find a stream candidate")
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
          (return-fn (first all-streams))))
      (do
        (log/info "Serving cached stream")
        (return-fn (first ordered-candidates))))))

(comment
  (def farts (atom {}))
  (deref farts)
  )