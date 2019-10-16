(ns whiplash.routes.services.stream
  (:require [ring.util.http-response :refer :all]
            [whiplash.integrations.pandascore :as pandascore]))

(defn get-stream
  [{:keys [params] :as req}]
  (ok (-> (pandascore/get-matches "rPMcxOQ-nPbL4rKOeZ8O8PBkZy6-0Ib4EAkHqxw2Gj16AvXuaJ4" :csgo)
          (pandascore/best-stream-candidate))))
