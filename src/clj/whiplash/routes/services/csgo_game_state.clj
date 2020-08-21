(ns whiplash.routes.services.csgo-game-state
  (:require [clojure.tools.logging :as log]
            [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [clojure.string :as string]
            [whiplash.config :refer [env]]
            [datomic.client.api :as d]))

;; TODO remove from source
;; TODO: make multiple requests when 100 or more streamers
(defn whiplash-streamers
  "usernames must be all lowercase"
  []
  (let [m {"akoozabazooka"    "jydPmMjLgMsjqovquKqVQNQEPKInwuNx"
           "jawbreakerplease" "tIaoxTuffKBzwLvgYheGmljDMgYaYeDC"
           "whiplash_gg"       "rZgNxwfwyEWmPTCwhNEaGHXSTxEroctZ"
           ;"birdfood"         "uHeyOMgIzPtouFlxZMBQmoasDZbKljbB"
           "rillo"            "ewzaBsSgilmffxiHjNQFjUNDvCfmCbuE"
           "huddlesworth"     "YtWaLjgnOIQPZdGBjaIEOcoqoPioVwQZ"
           ;"trixxytrix"       "AkxCnYSscmkQHwOBIYURmWJPSwxkklJA"
           ;"fitnesswiberg"    "ZxIjPtAlVHQWoQPcmJqTiOdnKWYfwklY"
           ;"wizarjry"         "eeAyWUZmxbEGPKmedvZonBOXjbCSBMWq"
           ;"mackenziey"       "pZCXYzqUAEohmwlLMBvgCmIPAonfHKMa"
           "involuntarylag"    "mQYSvROuqvgfhVaQBvppqvhISJeAVskZ"
           ;"lo0p__"             "UpWdvneFDxyHriCVzTANebkkQYgcDLXN"
           ;"xwordy"            "ybHNWyYkJPHSskxUmhxEcSykMscaXHVO"
           ;"hooligun_tv"       "mXTYKSpooiuNytmBUWSKOVPdaeABHdvD"
           ;"jojodj"            "FEbtbAcWVcLJmFtTamCyZISmFQxhfwJH"
           ;"2oldtogame"        "rpSppkZZLzvTPfDwGFujtvmQBFRHrOqU"
           }]
    (if (:prod env)
      m
      (assoc m "iyujzorpcrazwysxdjvnslittepwkxqs" "VXFOGLGUSETVZPECHRGTGLPECPNOEAON"))))

(def ^:const terrorists-win "Terrorists win this round")
(def ^:const counter-terrorists-win "Counter-Terrorists win this round")
(def ^:const kills "%s gets %s or more kills this round")
(def ^:const hs-kills "%s gets %s or more headshot kills this round")
(def ^:const dies "%s dies this round")
(def ^:const survives "%s survives this round")
(def ^:const bomb-planted "Bomb is planted this round")
(def ^:const bomb-defused "Bomb is defused this round")
(def ^:const bomb-explodes "Bomb explodes this round")

(def ^:private props
  [
   terrorists-win
   counter-terrorists-win
   dies
   survives
   bomb-planted
   bomb-defused
   bomb-explodes
   ;kills
   ;hs-kills
   ])

;;TODO: throw on failure
(defn parse-kills
  [text]
  (some->
    ;; only works for a single digit. regex matches kills and hs-kills.
    (re-find #" (\d) " text)
    second
    Integer/parseInt))

(comment
 (parse-kills counter-terrorists-win)
 (parse-kills (format kills "donny" 5)))

(defn proposition-result?
  [{:keys [event/channel-id proposition winning-team round-kills round-hs-kills player-health bomb previously] :as args}]
  ;(assert (contains? #{"T" "CT"} winning-team))
  (let [{:proposition/keys [text]} proposition
        n-kills (parse-kills text)]
    (log/info "proposition-result?" args n-kills)
    (condp = text
      terrorists-win
      (= "T" winning-team)

      counter-terrorists-win
      (= "CT" winning-team)

      (format kills channel-id n-kills)
      (>= round-kills n-kills)

      (format hs-kills channel-id n-kills)
      (>= round-hs-kills n-kills)

      (format dies channel-id)
      (let [prev-player-health (some-> previously :player :state :health)]
        ;; player dead
        (or (= 0 player-health)
            ;;prev player is the streamer
            (= 0 prev-player-health)))

      (format survives channel-id)
      (let [prev-player-health (some-> previously :player :state :health)]
        ;; player survives and has more than 0 health
        (and (< 0 player-health)
             ;; prev player is streamer
             (if (some? prev-player-health)
               (< 0 prev-player-health)
               true)))

      bomb-planted
      (or (= (some-> previously :round :bomb) "planted")
          (= "defused" bomb)
          (= "exploded" bomb))

      bomb-defused
      (= "defused" bomb)

      bomb-explodes
      (= "exploded" bomb))))

(defn- random-index
  "This exists mostly to redef it for tests"
  [coll]
  (rand-int (count coll)))

(defn- random-n-kills
  "Generate a random number between 1 inclusive and 5 inclusive"
  []
  (+ 1 (rand-int (- 6 1))))

(comment
  (->> (map (fn [x]
              (random-n-kills))
            (range 10000))
       (group-by identity)
       (map (fn [[k v]]
              {k (count v)}))
       (apply merge)))

(defn receive-from-game-client
  [{:keys [path-params body-params]}]
  (let [channel-id (some-> path-params :channel-id string/lower-case)]
    (if (= (get (whiplash-streamers) channel-id) (some-> body-params :auth :token))
      (let [db (d/db (:conn db/datomic-cloud))
            {:keys [event current-prop]} (db/pull-event-info
                                           {:db db
                                            :attrs [:db/id
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
          (do
            (log/info (dissoc body-params :auth))
            (cond
              (and (= "live" (some-> body-params :round :phase))
                   (or (= (format dies channel-id) (:proposition/text current-prop))
                       (= (format survives channel-id) (:proposition/text current-prop)))
                   (or (= 0 (some-> body-params :previously :player :state :health))
                       (= 0 (some-> body-params :player :state :health))))
              (let [{:keys [db-after]} (db/end-betting-for-proposition current-prop)]
                ;; if they die, resolve true, if they survive resolve false
                (db/payouts-for-proposition {:result? (= (format dies channel-id) (:proposition/text current-prop))
                                             :proposition current-prop
                                             :db db-after})
                (ok))

              (= "over" (some-> body-params :round :phase))
              (let [{:keys [db-after]} (db/end-betting-for-proposition current-prop)]
                (db/payouts-for-proposition {:result?     (proposition-result?
                                                            {:event/channel-id channel-id
                                                             :proposition      current-prop
                                                             :winning-team     (some-> body-params :round :win_team)
                                                             :round-kills      (some-> body-params :player :state :round_kills)
                                                             :round-hs-kills   (some-> body-params :player :state :round_killhs)
                                                             :player-health    (some-> body-params :player :state :health)
                                                             :bomb             (some-> body-params :round :bomb)
                                                             :previously       (some-> body-params :previously)})
                                             :proposition current-prop
                                             :db          db-after})
                (ok))

              (= "menu" (some-> body-params :player :activity))
              (do
                (db/cancel-proposition-and-return-cash {:proposition current-prop
                                                        :db          db})
                (ok))

              :else
              (no-content)))

          (nil? current-prop)
          (do
            (log/info (dissoc body-params :auth))
            (if (= "freezetime" (-> body-params :round :phase))
              (do
                (db/create-proposition {:text (let [chosen-prop (nth props (random-index props))]
                                                (cond
                                                  (string/includes? chosen-prop "kills this round")
                                                  (format chosen-prop channel-id (random-n-kills))

                                                  (string/includes? chosen-prop "%s")
                                                  (format chosen-prop channel-id)

                                                  :else
                                                  chosen-prop))
                                        :event-eid (:db/id event)
                                        :end-betting-secs 30})
                {:status 201
                 :headers nil
                 :body nil})
              (no-content)))))
      (do
        (log/errorf "channel-id %s has wrong token set" channel-id)
        (unauthorized)))))
