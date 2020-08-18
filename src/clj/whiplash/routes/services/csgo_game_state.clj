(ns whiplash.routes.services.csgo-game-state
  (:require [clojure.tools.logging :as log]
            [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [clojure.string :as string]
            [whiplash.config :refer [env]]))

;; TODO: end bets earlier than round end if the thing happens
;; props can have a new subcomponent :prop/csgo which holds {:csgo/round-number int}

;; TODO remove from source
;; TODO: make multiple requests when 100 or more streamers
(defn whiplash-streamers
  "usernames must be all lowercase"
  []
  (let [m {"akoozabazooka"    "jydPmMjLgMsjqovquKqVQNQEPKInwuNx"
           "jawbreakerplease" "tIaoxTuffKBzwLvgYheGmljDMgYaYeDC"
           ;"whiplash_gg"       "rZgNxwfwyEWmPTCwhNEaGHXSTxEroctZ"
           ;"birdfood"         "uHeyOMgIzPtouFlxZMBQmoasDZbKljbB"
           ;"rillo"            "ewzaBsSgilmffxiHjNQFjUNDvCfmCbuE"
           ;"huddlesworth"     "YtWaLjgnOIQPZdGBjaIEOcoqoPioVwQZ"
           ;"trixxytrix"       "AkxCnYSscmkQHwOBIYURmWJPSwxkklJA"
           ;"fitnesswiberg"    "ZxIjPtAlVHQWoQPcmJqTiOdnKWYfwklY"
           ;"qizarjry"         "eeAyWUZmxbEGPKmedvZonBOXjbCSBMWq"
           ;"mackenziey"       "pZCXYzqUAEohmwlLMBvgCmIPAonfHKMa"
           }]
    (if (:prod env)
      m
      (assoc m "iyujzorpcrazwysxdjvnslittepwkxqs" "VXFOGLGUSETVZPECHRGTGLPECPNOEAON"))))

(comment
  :player {:observer_slot 6,
           :weapons {:weapon_1 {:ammo_reserve 32,
                                :paintkit "am_bronze_sparkle",
                                :name "weapon_deagle",
                                :ammo_clip 7,
                                :type "Pistol",
                                :state "reloading",
                                :ammo_clip_max 7},
                     :weapon_0 {:paintkit "default", :name "weapon_knife_t", :type "Knife", :state "holstered"}},
           :name "Bazooka | whiplash.gg",
           :state {:money 650,
                   :helmet true,
                   :round_killhs 1,
                   :round_kills 1,
                   :smoked 0,
                   :equip_value 1700,
                   :health 100,
                   :armor 100,
                   :burning 0,
                   :flashed 0},
           :activity "playing",
           :team "T",
           :match_stats {:kills 1, :assists 0, :deaths 0, :mvps 0, :score 5},
           :steamid 76561198000333939},
  )

(def ^:const terrorists-win "Terrorists win this round")
(def ^:const counter-terrorists-win "Counter-Terrorists win this round")
;(def ^:private ^:const two-kills "%s gets 2 or more kills this round")

(def ^:private props
  [terrorists-win
   counter-terrorists-win])

(defn proposition-result?
  [{:keys [proposition winning-team]}]
  (assert (contains? #{"T" "CT"} winning-team))
  (let [{:proposition/keys [text]} proposition]
    (condp = text
      terrorists-win
      (= "T" winning-team)

      counter-terrorists-win
      (= "CT" winning-team))))

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
          (let [prop-text (:proposition/text current-prop)
                phase (some-> body-params :round :phase)
                winning-team (some-> body-params :round :win_team)
                ;round-kills (some-> body-params :player :state :round_kills)
                ]
            (log/info (dissoc body-params :auth))
            (if (= "over" phase)
              (let [{:keys [db-after]} (db/end-betting-for-proposition current-prop)]
                (db/payouts-for-proposition {:result? (proposition-result? {:proposition  current-prop
                                                                            :winning-team winning-team})
                                             :proposition current-prop
                                             :db          db-after})
                (ok))
              (no-content)))

          (nil? current-prop)
          (do
            (log/info (dissoc body-params :auth))
            (if (= "freezetime" (-> body-params :round :phase))
              (do
                (db/create-proposition {:text             (rand-nth props)
                                        :event-eid        (:db/id event)
                                        :end-betting-secs 30})
                {:status 201
                 :headers nil
                 :body nil})
              (no-content)))))
      (do
        (log/errorf "channel-id %s has wrong token set" channel-id)
        (unauthorized)))))
