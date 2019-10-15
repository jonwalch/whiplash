(ns whiplash.integrations.common
  (:require [clojure.data.json :as json]))

(defn resp->body
  [resp]
  (some-> resp :body (json/read-str :key-fn keyword)))

