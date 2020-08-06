(ns whiplash.routes.services.csgo-game-state
  (:require [clojure.tools.logging :as log]
            [ring.util.http-response :refer :all]))

(defn receive-from-game-client
  [{:keys [path-params body-params] :as req}]
  (log/info (:channel-id path-params))
  (log/info body-params)
  (ok))
