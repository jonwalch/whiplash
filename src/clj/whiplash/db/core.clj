(ns whiplash.db.core
  (:require
    [datomic.client.api :as d]
    [mount.core :refer [defstate]]
    [whiplash.config :refer [env]]
    [whiplash.time :as time]
    [clojure.tools.logging :as log]
    [whiplash.db.schemas :as schemas]
    [whiplash.payouts :as payouts]
    [clojure.string :as string]
    [clj-uuid :as uuid]
    [java-time :as jtime]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DO NOT USE DATOMIC ON PREM SCALAR OR COLLECTION FIND SYNTAX, IT'LL WORK LOCALLY BUT NOT IN PRODUCTION ;;
;; https://github.com/ComputeSoftware/datomic-client-memdb#caveats                                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private cloud-config
  {:server-type :cloud
   :region "us-west-2"
   :system "prod-whiplash-datomic"
   #_#_:creds-profile "<your_aws_profile_if_not_using_the_default>"
   :endpoint #_"http://vpce-083453c598589f6ba-s7g7d18a.vpce-svc-079c04a696f355e37.us-west-2.vpce.amazonaws.com:8182"
   "http://vpce-0e0c81e5c8220a09c-3y90v90k.vpce-svc-079c04a696f355e37.us-west-2.vpce.amazonaws.com:8182"
   })

(defn create-client
  [datomic-config]
  (if (:prod env)
    (do
      (log/debug "using prod client with config: %s" cloud-config)
      (d/client datomic-config))
    (do
      (log/debug "using dev memdb client")
      (require 'compute.datomic-client-memdb.core)
      (if-let [v (resolve 'compute.datomic-client-memdb.core/client)]
        (@v datomic-config)
        (throw (ex-info "compute.datomic-client-memdb.core is not on the classpath." {}))))))

(defn create-datomic-cloud
  []
  (let [client (create-client cloud-config)
        created? (d/create-database client {:db-name "whiplash"})
        conn (d/connect client {:db-name "whiplash"})
        ;; TODO: read current schema and only transact the schema if it has changed
        ;; TODO: transact all schemas one at a time instead of flattening and transacting them all at once
        schema-tx-result (d/transact conn {:tx-data (schemas/migrations->schema-tx)})]
    (log/debug "Migration to transact " (schemas/migrations->schema-tx))
    (log/debug "Schema transaction result " schema-tx-result)
    {:client client
     :conn conn}))

(defn destroy-datomic-cloud
  [datomic-cloud]
  (when-not (:prod env)
    (.close (:client datomic-cloud))))

(defstate datomic-cloud
          :start (create-datomic-cloud)
          :stop (destroy-datomic-cloud datomic-cloud))

(defn show-transaction
  "Show all the transaction data
   e.g.
    (-> conn show-transaction count)
    => the number of transaction"
  [conn]
  (seq (d/tx-range conn {})))

(defn add-user
  [{:keys [first-name last-name status email password user-name verify-token]}]
  (d/transact (:conn datomic-cloud)
              {:tx-data [{:user/first-name   first-name
                          :user/last-name    last-name
                          :user/name         user-name
                          :user/status       status
                          :user/verify-token verify-token
                          :user/email        email
                          :user/password     password
                          :user/sign-up-time (time/to-date)
                          :user/cash         500N}]}))

(defn create-unauthed-user
  [username]
  (d/transact (:conn datomic-cloud)
              {:tx-data [{:user/name         username
                          :user/status       :user.status/unauth
                          :user/sign-up-time (time/to-date)
                          :user/cash         500N}]}))

(defn update-password
  [conn {:keys [db/id password]}]
  (d/transact conn {:tx-data [{:db/id id
                               :user/password     password}]}))

(defn add-prop-bet-for-user
  [conn {:keys [db/id bet/amount user/cash bet/projected-result? bet/proposition]}]
  (d/transact conn {:tx-data [[:db/cas id :user/cash cash (bigint (- cash amount))]
                              {:db/id id
                               :user/prop-bets [{:bet/proposition proposition
                                                 :bet/projected-result? projected-result?
                                                 :bet/time       (time/to-date)
                                                 :bet/amount     (bigint amount)}]}]}))

(defn find-existing-prop-bet
  [{:keys [db user-id prop-id bet/projected-result?]}]
  ;; Consider finding all bets from prop instead of all bets from user and then filtering by prop
  (ffirst
    (d/q {:query '[:find (pull ?prop-bets [:db/id :bet/amount])
                   :in $ ?user-id ?prop-id ?projected-result?
                   :where [?user-id :user/prop-bets ?prop-bets]
                   [?prop-bets :bet/proposition ?prop-id]
                   [?prop-bets :bet/projected-result? ?projected-result?]]
          :args  [db user-id prop-id projected-result?]})))

(defn update-prop-bet-amount
  [{:keys [db/id bet/amount]} cash user-id additional-amount]
  ;; TODO: consider making bet times a vector to explicitly track these, we can just use datomics time function
  ;; to get them if we care for now
  (d/transact (:conn datomic-cloud)
              {:tx-data [[:db/cas user-id :user/cash cash (bigint (- cash additional-amount))]
                         {:db/id      id
                          :bet/time   (time/to-date)
                          :bet/amount (+ (bigint amount)
                                         (bigint additional-amount))}]}))

(defn verify-email
  [conn {:keys [db/id]}]
  (d/transact conn {:tx-data [{:db/id       id
                               :user/status :user.status/active
                               :user/verified-email-time (time/to-date)}]}))

(defn- find-user-by-email-db
  [db email]
  (d/q {:query '[:find ?user
                 :in $ ?email
                 :where [?user :user/email ?original-email]
                 [(.toLowerCase ^String ?original-email) ?lowercase-email]
                 [(= ?lowercase-email ?email)]]
        :args  [db (string/lower-case email)]}))

(defn- find-user-by-user-name-db
  [db user-name]
  (d/q {:query '[:find ?user
                 :in $ ?user-name
                 :where [?user :user/name ?original-name]
                 [(.toLowerCase ^String ?original-name) ?lowercase-name]
                 [(= ?lowercase-name ?user-name)]]
        :args  [db (string/lower-case user-name)]}))

;; TODO deprecate
(defn find-user-by-email
  [email]
  (when-let [user (ffirst (find-user-by-email-db (d/db (:conn datomic-cloud)) email))]
    user))

;; TODO deprecate
(defn find-user-by-user-name
  [user-name]
  (let [db (d/db (:conn datomic-cloud))]
    (when-let [user (ffirst (find-user-by-user-name-db db user-name))]
      user)))

(defn pull-user
  [{:keys [user/name user/email db attrs]}]
  (assert (or name email))
  (let [lookup (if name
                 [:user/name name]
                 [:user/email email])
        db (or db (d/db (:conn datomic-cloud)))
        result (ffirst
                 (d/q {:query '[:find (pull ?user attrs)
                                :in $ ?keyw ?user-identifier attrs
                                :where [?user ?keyw ?original-name]
                                [(.toLowerCase ^String ?original-name) ?lowercase-name]
                                [(= ?lowercase-name ?user-identifier)]]
                       :args  [db
                               (first lookup)
                               (string/lower-case (second lookup))
                               attrs]}))]
    (if (contains? result :user/status)
      (update result :user/status :db/ident)
      result)))

(defn pull-unacked-notifications
  [{:keys [db/id db attrs notification/types]}]
  (->> (d/q {:query '[:find (pull ?notif attrs)
                      :in $ ?id attrs ?types
                      :where [?id :user/notifications ?notif]
                      [?notif :notification/acknowledged? false]
                      [?notif :notification/type ?type]
                      [?type :db/ident ?atype]
                      [(contains? ?types ?atype)]]
             :args  [db id attrs types]})
       (map (fn [notif]
              (-> notif first (update :notification/type :db/ident))))))

(defn find-bets
  [db user-name game-id match-id]
  (->> (d/q {:query '[:find ?bet
                      :in $ ?user-name ?game-id ?match-id
                      :where [?user :user/name ?user-name]
                      [?user :user/bets ?bet]
                      [?bet :game/id ?game-id]
                      [?bet :match/id ?match-id]]
             :args [db user-name game-id match-id]})
       (map first)))

(defn find-prop-bets-for-user
  [{:keys [db user-id prop-bet-id]}]
  (->> (d/q
         {:query '[:find (pull ?bet [:bet/amount :bet/time :bet/projected-result?])
                   :in $ ?user-id ?prop-bet-id
                   :where [?user-id :user/prop-bets ?bet]
                   [?bet :bet/proposition ?prop-bet-id]]
          :args  [db user-id prop-bet-id]})
       (map first)))

(defn pull-bet-payout-info
  [{:keys [db prop-bet-id attrs]}]
  (->> (d/q
         {:query '[:find (pull ?bet attrs)
                   :in $ ?prop-bet-id attrs
                   :where [?bet :bet/proposition ?prop-bet-id]]
          :args  [db prop-bet-id attrs]})
       (map
         (comp (fn [bet]
                 (-> bet
                     (assoc :user/db-id (get-in bet [:user/_prop-bets :db/id])
                            :user/cash (get-in bet [:user/_prop-bets :user/cash])
                            :user/status (get-in bet [:user/_prop-bets :user/status :db/ident]))
                     (dissoc :user/_prop-bets)))
               first))))

(defn find-all-user-bets-for-proposition
  [{:keys [db prop-bet-id]}]
  (d/q
    {:query '[:find (pull ?bet [:bet/amount
                                :bet/projected-result?
                                {:user/_prop-bets [:user/name]}])
              :in $ ?prop-bet-id
              :where [?bet :bet/proposition ?prop-bet-id]]
     :args  [db prop-bet-id]}))

(defn find-all-user-bets-for-event
  [{:keys [db event-id]}]
  (ffirst
    (d/q
      {:query '[:find (pull ?event-id [{:event/propositions
                                        [{:bet/_proposition [:bet/amount
                                                             :bet/payout
                                                             :bet/projected-result?
                                                             {:user/_prop-bets [:user/name
                                                                                {:user/status [:db/ident]}]}]}]}])
                :in $ ?event-id]
       :args  [db event-id]})))

(defn find-all-time-leaderboard
  []
  (let [db (d/db (:conn datomic-cloud))]
    (->> (d/q
           {:query '[:find ?user-name ?cash
                     :in $
                     :where [?user :user/cash ?cash]
                     [?user :user/name ?user-name]
                     [(>= ?cash 500N)]
                     (not [?user :user/status :user.status/unauth])]
            :args  [db]})
         (map #(hash-map :user_name (first %)
                         :cash (second %)))
         (sort-by :cash #(compare %2 %1)))))

(defn find-top-n
  [n]
  (->> (find-all-time-leaderboard)
       (take n)))

;; TODO: adjust threshold at a later time and add a test that won't return n days later
;; We're pull n and then figuring out the largest because aggregation doesn't work with :db/ids
(defn find-last-event
  ([]
   (find-last-event (d/db (:conn datomic-cloud))))
  ([db]
   (->>
     (d/q {:query '[:find ?event ?end-time
                    :in $ ?threshold
                    :where [?event :event/end-time ?end-time]
                    [(> ?end-time ?threshold)]
                    [?event :event/running? false]]
           :args  [db (time/to-date (time/days-delta -60))]})
     (sort-by second #(compare %2 %1))
     ffirst)))

;; Prop betting MVP db functions

(defn find-ongoing-event
  ([]
   (find-ongoing-event (d/db (:conn datomic-cloud))))
  ([db]
   (ffirst
     (d/q {:query '[:find ?event
                    :where [?event :event/running? true]]
           :args  [db]}))))

(defn pull-ongoing-event
  [{:keys [db attrs]}]
  (let [db (or db (d/db (:conn datomic-cloud)))
        result (ffirst
                 (d/q {:query '[:find (pull ?event attrs)
                                :in $ attrs
                                :where [?event :event/running? true]]
                       :args  [db attrs]}))]
    (if (contains? result :event/stream-source)
      (update result :event/stream-source :db/ident)
      result)))

(defn pull-next-event-time
  "assumes only one of these exists in the db"
  [{:keys [db attrs]}]
  (let [db (or db (d/db (:conn datomic-cloud)))]
    (ffirst
      (d/q {:query '[:find (pull ?e attrs)
                     :in $ attrs
                     :where [?e :whiplash/next-event-time _]]
            :args  [db attrs]}))))

(defn pull-undismissed-suggestions-for-ongoing-event
  [{:keys [db attrs]}]
  (let [db (or db (d/db (:conn datomic-cloud)))]
    (->> (d/q {:query '[:find (pull ?suggestions attrs)
                          :in $ attrs
                          :where [?event :event/running? true]
                          [?event :event/suggestions ?suggestions]
                          [?suggestions :suggestion/dismissed? false]]
                 :args  [db attrs]})
         (apply concat)
         (map (fn [{:keys [suggestion/user] :as suggestion}]
                (if user
                  (-> suggestion
                      (assoc :user/name (:user/name user))
                      (dissoc :suggestion/user))
                  suggestion))))))

(defn create-event
  [{:keys [title channel-id source]}]
  (assert (or (= :event.stream-source/twitch source)
              (= :event.stream-source/youtube source)
              (= :event.stream-source/cnn-unauth source)))
  (d/transact (:conn datomic-cloud)
              {:tx-data [{:db/id            "temp"
                          :event/title      title
                          :event/channel-id channel-id
                          :event/stream-source source
                          :event/running?   true
                          :event/start-time (time/to-date)}
                         {:whiplash/events ["temp"]}]}))

(defn end-event
  [event-id]
  (d/transact (:conn datomic-cloud)
              {:tx-data [{:db/id event-id
                          :event/running? false
                          :event/end-time (time/to-date)}]}))

;; TODO deprecate
(defn find-ongoing-proposition
  ([]
   (find-ongoing-proposition (d/db (:conn datomic-cloud))))
  ([db]
   (ffirst
     (d/q {:query '[:find ?prop
                    :where [?prop :proposition/running? true]]
           :args  [db]}))))

;; TODO make this depend on an event
(defn pull-ongoing-proposition
  [{:keys [db attrs]}]
  (let [db (or db (d/db (:conn datomic-cloud)))]
    (ffirst
      (d/q {:query '[:find (pull ?prop attrs)
                     :in $ attrs
                     :where [?prop :proposition/running? true]]
            :args  [db attrs]}))))

(defn pull-previous-proposition
  [{:keys [db event-eid attrs]}]
  (->> (d/q {:query '[:find (pull ?prop attrs) ?ts
                      :in $ ?event-eid attrs
                      :where [?event-eid :event/propositions ?prop]
                      [?prop :proposition/running? false]
                      [?prop :proposition/end-time ?ts]]
             :args  [db event-eid attrs]})
       (sort-by second #(compare %2 %1))
       ffirst))

(defn create-proposition
  [{:keys [text event-eid end-betting-secs]}]
  (let [now (time/now)]
    (d/transact (:conn datomic-cloud)
                {:tx-data
                 [{:db/id              event-eid
                   :event/propositions [{:proposition/text       text
                                         :proposition/start-time (time/to-date now)
                                         :proposition/betting-end-time (time/to-date
                                                                         (time/seconds-delta now end-betting-secs))
                                         :proposition/running?   true}]}]})))

#_(defn end-betting-for-proposition
  [{:keys [proposition-eid]}]
  (d/transact (:conn datomic-cloud)
              {:tx-data [{:db/id                        proposition-eid
                          :proposition/betting-end-time (time/to-date)}]}))

(defonce ^:private winning-constant 10)

(defn- calculate-payout-with-bonus
  [payout result? side->payout-bonus-map]
  (if (< 0 payout)
    (+ payout
       (or (get side->payout-bonus-map result?)
           0))
    payout))

(defn user-id->total-pay
  ([payouts]
   (user-id->total-pay payouts nil {}))
  ([payouts result? side->payout-bonus-map]
   (->> payouts
        (group-by :user/db-id)
        (map (fn [[db-id pbets]]
               (let [payout (apply + 0 (keep :bet/payout pbets))]
                 {db-id {:user/total-payout (calculate-payout-with-bonus payout
                                                                         result?
                                                                         side->payout-bonus-map)
                         :user/cash         (:user/cash (first pbets))
                         :user/status       (:user/status (first pbets))}})))
        (apply merge))))

(defn generate-user-cash-txs
  [{:keys [user-id->total-payout flip?]}]
  (->> user-id->total-payout
       (map
         (fn [[user-id {:keys [user/cash user/total-payout user/status]}]]
           (let [new-balance (+ cash total-payout)
                 authed-user? (not= status :user.status/unauth)
                 bailout? (> 100 new-balance)
                 cas [:db/cas user-id :user/cash cash (if (and authed-user?
                                                               bailout?
                                                               (not flip?))
                                                        100N
                                                        new-balance)]]
             (cond
               (and bailout? (not authed-user?) (not flip?))
               [cas
                {:db/id              user-id
                 :user/notifications [{:notification/type :notification.type/no-bailout
                                       :notification/acknowledged? false}]}]

               (and bailout? (not flip?))
               [cas
                {:db/id              user-id
                 :user/notifications [{:notification/type :notification.type/bailout
                                       :notification/acknowledged? false}]}]

               :else
               [cas]))))
       (apply concat)
       (into [])))

(defn generate-payout-txs
  ([payouts processed-time]
   (generate-payout-txs payouts processed-time {}))
  ([payouts processed-time side->payout-bonus-map]
   (->> payouts
        (map
          (fn [{:keys [user/db-id db/id] :as p}]
            (let [tx (-> p
                         (assoc :bet/processed-time processed-time)
                         (update :bet/payout #(calculate-payout-with-bonus %
                                                                           (:bet/projected-result? p)
                                                                           side->payout-bonus-map))
                         (dissoc :user/cash :user/db-id :bet/amount :bet/projected-result? :user/status))]
              (if (> (:bet/payout tx) 0N)
                [tx
                 {:db/id              db-id
                  :user/notifications [{:notification/type          :notification.type/payout
                                        :notification/trigger       id
                                        :notification/acknowledged? false}]}]
                [tx]))))
        (apply concat)
        (into []))))

(defn side->payout-bonus
  "Calculate how much additional whipcash the winning side gets.
  Additional cash (AC) = (constant * number of betters on that side)"
  [bets result?]
  (->> bets
       (group-by :bet/projected-result?)
       (map (fn [[projected-result? bets]]
              {projected-result? (if (= result? projected-result?)
                                   (* winning-constant (count bets))
                                   0)}))
       (apply merge)))

(defn generate-prop-payout-txs
  [{:keys [proposition result? db]}]
  (let [bets (pull-bet-payout-info {:db db
                                    :prop-bet-id        (:db/id proposition)
                                    :attrs [:db/id :bet/amount :bet/projected-result?
                                            {:user/_prop-bets [:user/cash
                                                               :db/id
                                                               {:user/status [:db/ident]}]}]})
        side->payout-bonus-map (side->payout-bonus bets result?)
        payouts (map
                  (fn [{:keys [bet/projected-result? bet/amount] :as bet}]
                    (assoc bet
                      :bet/payout
                      (or (payouts/payout-for-bet
                            {:bet-stats   (-> bets
                                              (payouts/game-bet-totals :bet/projected-result?)
                                              (payouts/team-odds))
                             :bet/amount  amount
                             :team/id     projected-result?
                             :team/winner result?})
                          0N)))
                  bets)
        user-id->total-payout (user-id->total-pay payouts result? side->payout-bonus-map)
        processed-time (time/to-date)
        payout-txs (generate-payout-txs payouts processed-time side->payout-bonus-map)
        user-cash-txs (generate-user-cash-txs {:user-id->total-payout user-id->total-payout})]
    (vec
      (concat payout-txs
              user-cash-txs
              ;; Other info is transacted in end-betting-for-proposition
              [{:db/id              (:db/id proposition)
                :proposition/result (if result?
                                      :proposition.result/true
                                      :proposition.result/false)}]))))

(defn end-betting-for-proposition
  [{:keys [db/id proposition/betting-end-time]}]
  (let [end-time (time/now)
        inst-end-time (time/to-date end-time)]
    (d/transact (:conn datomic-cloud)
                {:tx-data [(merge
                             {:db/id                id
                              :proposition/running? false
                              :proposition/end-time inst-end-time}
                             (when (jtime/before? end-time
                                                  (time/date-to-zdt betting-end-time))
                               {:proposition/betting-end-time inst-end-time}))]})))

(defn payouts-for-proposition
  [arg-map]
  (d/transact (:conn datomic-cloud)
              {:tx-data (generate-prop-payout-txs arg-map)}))

(defn- generate-txs-to-cancel-proposition
  [{:keys [db proposition]}]
  (let [payouts (->> (pull-bet-payout-info
                       {:db          db
                        :prop-bet-id (:db/id proposition)
                        :attrs       [:db/id :bet/amount
                                      {:user/_prop-bets [:user/cash
                                                         :db/id
                                                         {:user/status [:db/ident]}]}]})
                     (map (fn [{:keys [bet/amount] :as bet}]
                            (assoc bet :bet/payout amount))))
        user-id->total-payout (user-id->total-pay payouts)
        processed-time (time/to-date)
        payout-txs (generate-payout-txs payouts processed-time)
        user-cash-txs (generate-user-cash-txs {:user-id->total-payout user-id->total-payout})]
    (vec
      (concat payout-txs
              user-cash-txs
              [{:db/id                (:db/id proposition)
                :proposition/result   :proposition.result/cancelled}]))))

(defn cancel-proposition-and-return-cash
  [arg-map]
  (d/transact (:conn datomic-cloud)
              {:tx-data (generate-txs-to-cancel-proposition arg-map)}))

(defn add-user-suggestion-to-event
  [{:keys [text event-eid user-eid]}]
  (d/transact (:conn datomic-cloud)
              {:tx-data [{:db/id              event-eid
                          :event/suggestions [{:suggestion/text       text
                                               :suggestion/submission-time (time/to-date)
                                               :suggestion/dismissed?   false
                                               :suggestion/user user-eid
                                               :suggestion/uuid (uuid/v4)}]}]}))

(defn find-target-suggestions
  [{:keys [db uuids]}]
  (->>
    (d/q {:query '[:find ?suggestion
                   :in $ ?uuids
                   :where [?suggestion :suggestion/dismissed? false]
                   [?suggestion :suggestion/uuid ?uuid]
                   [(contains? ?uuids ?uuid)]]
          :args  [db (set uuids)]})
    (mapcat identity)))

(defn dismiss-suggestions
  [suggestion-eids]
  (let [dismissed-time (time/to-date)
        txs (mapv
              (fn [eid]
                {:db/id                 eid
                 :suggestion/dismissed? true
                 :suggestion/dismissed-time dismissed-time})
              suggestion-eids)]
    (d/transact (:conn datomic-cloud) {:tx-data txs})))

(defn create-recovery-token
  [{:keys [user-id recovery/token]}]
  (d/transact (:conn datomic-cloud)
              {:tx-data [{:db/id         user-id
                          :user/recovery [{:recovery/token       token
                                           :recovery/issued-time (time/to-date)}]}]}))

(comment

  (def ^:private local-tunnel-cloud-config
    {:server-type :cloud
     :region "us-west-2"
     :system "prod-whiplash-datomic"
     ;; for local tunnel
     :endpoint "http://entry.prod-whiplash-datomic.us-west-2.datomic.net:8182"
     ;; :proxy-port is only for local tunnel
     :proxy-port 8182})

  (def test-client (d/client local-tunnel-cloud-config))
  (def conn (d/connect test-client {:db-name "whiplash"}))

  (->>
    (d/q {:query '[:find (pull ?user [:user/email :user/name :user/sign-up-time])
                   :in $
                   :where [?user :user/status :user.status/active]]
          :args  [(d/db conn)]})

    (apply concat)
    (sort-by :user/sign-up-time))

  (defn find-loser-by-email
    [email conn]
    (when-let [user (ffirst (find-user-by-email-db (d/db conn) email))]
      user))

  (defn make-admin
    [email conn]
    (let [user (find-loser-by-email email conn)]
      (d/transact conn {:tx-data [{:db/id       user
                                   :user/status :user.status/admin}]})))

  (find-loser-by-email "foobar@whiplashesports.com" conn)
  (make-admin "foobar@whiplashesports.com" conn)

  #_(d/create-database test-client {:db-name "test"})
  #_(d/delete-database test-client {:db-name "foo"})
  )
