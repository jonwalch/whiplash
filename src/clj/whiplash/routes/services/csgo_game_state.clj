(ns whiplash.routes.services.csgo-game-state
  (:require [clojure.tools.logging :as log]
            [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [clojure.string :as string]
            [whiplash.config :refer [env]]))

;; props can have a new subcomponent :prop/csgo which holds {:csgo/round-number int}

;; TODO remove from source
;; TODO: make multiple requests when 100 or more streamers
(defn whiplash-streamers
  "usernames must be all lowercase"
  []
  (let [m {"akoozabazooka"    "jydPmMjLgMsjqovquKqVQNQEPKInwuNx"
           "jawbreakerplease" "tIaoxTuffKBzwLvgYheGmljDMgYaYeDC"
           "birdfood"         "uHeyOMgIzPtouFlxZMBQmoasDZbKljbB"
           "rillo"            "ewzaBsSgilmffxiHjNQFjUNDvCfmCbuE"
           "huddlesworth"     "YtWaLjgnOIQPZdGBjaIEOcoqoPioVwQZ"
           "trixxytrix"       "AkxCnYSscmkQHwOBIYURmWJPSwxkklJA"
           "fitnesswiberg"    "ZxIjPtAlVHQWoQPcmJqTiOdnKWYfwklY"
           "qizarjry"         "eeAyWUZmxbEGPKmedvZonBOXjbCSBMWq"
           "mackenziey"       "pZCXYzqUAEohmwlLMBvgCmIPAonfHKMa"}]
    (if (:prod env)
      m
      (assoc m "iyujzorpcrazwysxdjvnslittepwkxqs" "VXFOGLGUSETVZPECHRGTGLPECPNOEAON"))))

(defn receive-from-game-client
  [{:keys [path-params body-params]}]
  (let [channel-id (some-> path-params :channel-id string/lower-case)]
    (if (= (get (whiplash-streamers) channel-id) (some-> body-params :auth :token))
      (let [{:keys [event current-prop]} (db/pull-event-info
                                           {:attrs [:db/id
                                                    :event/stream-source
                                                    :event/channel-id
                                                    :event/auto-run
                                                    {:event/propositions
                                                     '[:db/id
                                                       :proposition/start-time
                                                       :proposition/text
                                                       :proposition/running?
                                                       :proposition/betting-end-time
                                                       {:proposition/result [:db/ident]}]}]
                                            :event/channel-id channel-id})]
        (cond
          (or (not= channel-id (:event/channel-id event))
              (not= :event.stream-source/twitch (:event/stream-source event))
              (not= :event.auto-run/csgo (:event/auto-run event)))
          (do
            (log/infof "ignoring event from %s" channel-id)
            (no-content))

          ;; TODO: cancel bet if round number (-> body :map :round) changes but we didnt see :phase "over" on :round
          ;; requires tracking the round number
          (some? current-prop)
          (let [phase (some-> body-params :round :phase)
                winning-team (some-> body-params :round :win_team)]
            (log/info (dissoc body-params :auth))
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
            (log/info (dissoc body-params :auth))
            (if (= "freezetime" (-> body-params :round :phase))
              (do
                (db/create-proposition {:text             "Terrorists win this round"
                                        :event-eid        (:db/id event)
                                        :end-betting-secs 30})
                {:status 201
                 :headers nil
                 :body nil})
              (no-content)))))
      (do
        (log/errorf "channel-id %s has wrong token set" channel-id)
        (unauthorized)))))
