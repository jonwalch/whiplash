(ns whiplash.test.common
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [whiplash.middleware.formats :as formats]
            [mount.core :as mount]))

(defn app-fixtures
  []
  (use-fixtures
    :once
    (fn [f]
      (mount/start #'whiplash.config/env
                   #'whiplash.handler/app-routes)
      (f))))

(defn db-fixtures
  []
  (use-fixtures
    :each
    (fn [f]
      (mount/start #'whiplash.db.core/conn)
      (f)
      (mount/stop #'whiplash.db.core/conn))))

(defn parse-json-body
  [{:keys [body] :as req}]
  ;(assert body)
  (m/decode formats/instance "application/json" body))

