(ns whiplash.time
  (:require [java-time :as time]))

;; TODO unit tests

(defn now
  []
  (time/with-zone-same-instant (time/zoned-date-time) "UTC"))

(defn days-ago
  ([days]
   (time/minus (now) (time/days days)))
  ([start days]
   (time/minus start (time/days days))))

(defn days-ago-trunc
  ([days]
   (time/truncate-to (days-ago days) :days))
  ([start days]
   (time/truncate-to (days-ago start days) :days)))

(defn days-in-future
  ([days]
   (time/plus (now) (time/days days)))
  ([start days]
   (time/plus start (time/days days))))

(defn days-in-future-trunc
  ([days]
   (time/truncate-to (days-in-future days) :days))
  ([start days]
   (time/truncate-to (days-in-future start days) :days)))

(defn date-iso-string
  [date]
  (time/format (time/formatter :iso-instant) date))
