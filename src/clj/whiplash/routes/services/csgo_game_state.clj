(ns whiplash.routes.services.csgo-game-state
  (:require [clojure.tools.logging :as log]
            [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]))

;; add ability to toggle auto-run? on admin panel, admins only
;; props can have a new subcomponent :prop/csgo which holds {:csgo/round-number int}

;; TODO add check for proper auth token corresponding with proper channel-id
(defn receive-from-game-client
  [{:keys [path-params body-params] :as req}]
  (let [channel-id (:channel-id path-params)
        {:keys [event current-prop]} (db/pull-event-info
                                       {:attrs [:db/id
                                                :event/stream-source
                                                :event/channel-id
                                                {:event/propositions
                                                 '[:db/id
                                                   :proposition/start-time
                                                   :proposition/text
                                                   :proposition/running?
                                                   :proposition/betting-end-time
                                                   {:proposition/result [:db/ident]}]}]
                                        :event/channel-id channel-id})]
    (cond
      ;;TODO add false? event/auto-run? to or
      ;; TODO: lowercase these comparison
      (or (not= channel-id (:event/channel-id event))
          (not= :event.stream-source/twitch (:event/stream-source event)))
      (no-content)

      ;; TODO: cancel bet if round number (-> body :map :round) changes but we didnt see :phase "over" on :round
      ;; requires tracking the round number
      (some? current-prop)
      (let [phase (some-> body-params :round :phase)
            winning-team (some-> body-params :round :win_team)]
        (log/infof "%s" body-params)
        (if (= "over" phase)
          (do
            (assert (contains? #{"T" "CT"} winning-team))
            (let [{:keys [db-after]} (db/end-betting-for-proposition current-prop)]
              (db/payouts-for-proposition {:result?     (= "T" winning-team)
                                           :proposition current-prop
                                           :db          db-after}))
            (ok))
          (no-content)))

      (nil? current-prop)
      (do
        (log/infof "%s" body-params)
        (if (= "freezetime" (-> body-params :round :phase))
          (do
            (db/create-proposition {:text             "Terrorists win this round"
                                    :event-eid        (:db/id event)
                                    :end-betting-secs 30})
            {:status 201
             :headers nil
             :body nil})
          (no-content))))))
