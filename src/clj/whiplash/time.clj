(ns whiplash.time
  (:require [java-time :as time]))

;; TODO unit tests

(defn now
  []
  (time/with-zone-same-instant (time/zoned-date-time) "UTC"))

(defn to-millis
  ([] (to-millis (now)))
  ([date] (time/to-millis-from-epoch date)))

(defn days-ago
  ([days]
   (days-ago (now) days))
  ([start days]
   (time/minus start (time/days days))))

(defn days-ago-trunc
  ([days]
   (days-ago-trunc (now) days))
  ([start days]
   (time/truncate-to (days-ago start days) :days)))

(defn days-in-future
  ([days]
   (days-in-future (now) days))
  ([start days]
   (time/plus start (time/days days))))

(defn days-in-future-trunc
  ([days]
   (days-in-future-trunc (now) days))
  ([start days]
   (time/truncate-to (days-in-future start days) :days)))

(defn date-iso-string
  [date]
  (time/format (time/formatter :iso-instant) date))

(defn http-date-str
  [date]
  (time/format (time/formatter :rfc-1123-date-time) date))

(comment (-> (days-in-future 7) time/to-millis-from-epoch)
         (http-date-str (now)))
