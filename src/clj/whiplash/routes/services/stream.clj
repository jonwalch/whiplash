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
          (atom {}))

(defn get-stream
  [{:keys [params] :as req}]
  ;; TODO check atom if too old get again
  (let [{:keys [streams/last-fetch streams/ordered-candidates]} (deref cached-streams)]
    (if (or (nil? last-fetch)
            (java-time/after? (time/now) (time/minutes-delta last-fetch 1)))
      (do
        (log/info "Fetching streams")
        (let [all-streams (-> :csgo
                              pandascore/get-matches
                              pandascore/sort-and-transform-stream-candidates)
              best-stream (first all-streams)]
          (when (nil? best-stream)
            (log/info "Couldn't find a stream candidate"))
          (reset! cached-streams {:streams/last-fetch (time/now)
                                  :streams/ordered-candidates    all-streams})
          (ok (or best-stream {}))))
      (do
        (log/info "Serving cached stream")
        (ok (or (first ordered-candidates) {}))))))

(comment
  (def farts (atom {}))
  (deref farts)
  )