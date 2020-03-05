(ns whiplash.time
  (:require [java-time :as time])
  (:import [java.time ZonedDateTime ZoneId]))

;; TODO unit tests

(defn now
  []
  (time/with-zone-same-instant (time/zoned-date-time) "UTC"))

(defn timestamp-to-zdt
  [timestamp-str]
  (time/with-zone-same-instant (time/zoned-date-time timestamp-str) "UTC"))

(defn to-millis
  ([] (to-millis (now)))
  ([date] (time/to-millis-from-epoch date)))

(defn days-delta
  ([days]
   (days-delta (now) days))
  ([start days]
   (time/plus start (time/days days))))

(defn days-delta-trunc
  "Returns ZonedDateTime truncated to the day. i.e. 2019-10-07T00:00Z[UTC]"
  ([days]
   (days-delta-trunc (now) days))
  ([start days]
   (time/truncate-to (days-delta start days) :days)))

(defn minutes-delta
  ([minutes]
   (minutes-delta (now) minutes))
  ([start minutes]
   (time/plus start (time/minutes minutes))))

(defn hours-delta
  ([hours]
   (hours-delta (now) hours))
  ([start hours]
   (time/plus start (time/hours hours))))

(defn seconds-delta
  ([seconds]
   (seconds-delta (now) seconds))
  ([start seconds]
   (time/plus start (time/seconds seconds))))

(defn date-iso-string
  [date]
  (time/format (time/formatter :iso-instant) date))

(defn http-date-str
  [date]
  (time/format (time/formatter :rfc-1123-date-time) date))

(defn last-monday
  "Returns truncated ZonedDateTime of the previous monday. Returns today if it is monday."
  []
  (.with (days-delta-trunc 0) (time/day-of-week :monday)))

(defn next-monday
  "Returns truncated ZonedDateTime of next monday."
  []
  (.with (days-delta-trunc 7) (time/day-of-week :monday)))

(defn to-date
  "Takes a zoned date time and turns it into a java.util.Date/clojure inst"
  ([]
   (to-date (now)))
  ([start]
   (time/java-date start)))

(defn date-to-zdt
  "Takes a java.util.Date/clojure inst and turns it into a ZonedDateTime"
  [date]
  (ZonedDateTime/ofInstant (.toInstant date) (ZoneId/of "UTC")))

(comment
  (minutes-delta 1)
  (time/zoned-date-time "2019-10-12T07:47:11Z" "UTC")
  (time/with-zone-same-instant (time/zoned-date-time "2019-10-12T07:47:11Z") "UTC")
  (inst?
    (time/java-date (now)))
  (time/before? (now) (days-delta 1))
  )

