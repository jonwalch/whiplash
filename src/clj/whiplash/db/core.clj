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
    [clj-uuid :as uuid]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DO NOT USE DATOMIC ON PREM SCALAR OR COLLECTION FIND SYNTAX, IT'LL WORK LOCALLY BUT NOT IN PRODUCTION ;;
;; https://github.com/ComputeSoftware/datomic-client-memdb#caveats                                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private cloud-config
  {:server-type :cloud
   :region "us-west-2"
   :system "prod-whiplash-datomic"
   #_#_:creds-profile "<your_aws_profile_if_not_using_the_default>"
   :endpoint "http://vpce-083453c598589f6ba-s7g7d18a.vpce-svc-079c04a696f355e37.us-west-2.vpce.amazonaws.com:8182"})

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
  [conn {:keys [first-name last-name status email password user-name verify-token]}]
  (d/transact conn {:tx-data [{:user/first-name   first-name
                               :user/last-name    last-name
                               :user/name         user-name
                               :user/status       status
                               :user/verify-token verify-token
                               :user/email        email
                               :user/password     password
                               :user/sign-up-time (time/to-date)
                               :user/cash         500N}]}))

(defn update-password
  [conn {:keys [db/id password]}]
  (d/transact conn {:tx-data [{:db/id id
                               :user/password     password}]}))

(defn add-guess-for-user
  [conn {:keys [db/id game-type match-name game-id team-name team-id match-id bet-amount cash]}]
  (d/transact conn {:tx-data [[:db/cas id :user/cash cash (bigint (- cash bet-amount))]
                              {:db/id     id
                               :user/bets [{:bet/time       (time/to-date)
                                            :bet/processed? false
                                            :game/type      game-type
                                            :game/id        game-id
                                            :match/name     match-name
                                            :match/id       match-id
                                            :team/name      team-name
                                            :team/id        team-id
                                            :bet/amount     (bigint bet-amount)}]}]}))

(defn add-prop-bet-for-user
  [conn {:keys [db/id bet/amount user/cash bet/projected-result? bet/proposition]}]
  (d/transact conn {:tx-data [[:db/cas id :user/cash cash (bigint (- cash amount))]
                              {:db/id id
                               :user/prop-bets [{:bet/proposition proposition
                                                 :bet/projected-result? projected-result?
                                                 :bet/time       (time/to-date)
                                                 :bet/amount     (bigint amount)}]}]}))

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
      (update result :user/status #(:db/ident %))
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
              (-> notif first (update :notification/type #(:db/ident %)))))))

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
  [{:keys [db prop-bet-id]}]
  (->> (d/q
         {:query '[:find (pull ?bet [:db/id :bet/amount :bet/projected-result?
                                     {:user/_prop-bets [:user/cash :db/id]}])
                   :in $ ?prop-bet-id
                   :where [?bet :bet/proposition ?prop-bet-id]]
          :args  [db prop-bet-id]})
       (map
         (comp (fn [bet]
                 (-> bet
                     (assoc :user/db-id (get-in bet [:user/_prop-bets :db/id])
                            :user/cash (get-in bet [:user/_prop-bets :user/cash]))
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
                                                             {:user/_prop-bets [:user/name]}]}]}])
                :in $ ?event-id]
       :args  [db event-id]})))


(defn find-all-time-leaderboard
  []
  (let [db (d/db (:conn datomic-cloud))]
    (->> (d/q
           {:query '[:find ?user-name ?cash
                     :in $
                     :where [?user :user/cash ?cash]
                     [?user :user/name ?user-name]]
            :args  [db]})
         (map #(hash-map :user_name (first %)
                         :cash (second %)))
         (sort-by :cash #(compare %2 %1)))))

(defn find-top-ten
  []
  (->> (find-all-time-leaderboard)
       (take 10)))

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
      (update result :event/stream-source #(:db/ident %))
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
  (ffirst
    (d/q {:query '[:find (pull ?prop attrs)
                   :in $ attrs
                   :where [?prop :proposition/running? true]]
          :args  [db attrs]})))

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

(defn end-betting-for-proposition
  [{:keys [proposition-eid]}]
  (d/transact (:conn datomic-cloud)
              {:tx-data [{:db/id                        proposition-eid
                          :proposition/betting-end-time (time/to-date)}]}))

(defn end-proposition
  [{:keys [proposition result? db]}]
  (let [betting-end-time (:proposition/betting-end-time proposition)
        bets (pull-bet-payout-info {:db db
                                    :prop-bet-id        (:db/id proposition)})
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
        user-id->total-payout (->> payouts
                                   (group-by :user/db-id)
                                   (map (fn [[db-id pbets]]
                                          {db-id {:user/total-payout (apply +
                                                                            0
                                                                            (keep :bet/payout pbets))
                                                  :user/cash (:user/cash (first pbets))}}))
                                   (apply merge))
        processed-time (time/to-date)
        payout-txs (->> payouts
                        (map
                          (fn [{:keys [bet/payout user/db-id db/id] :as p}]
                            (let [tx (-> p
                                         (dissoc :user/cash :user/db-id :bet/amount :bet/projected-result?)
                                         (assoc :bet/processed-time processed-time))]
                              (if (> payout 0N)
                                [tx
                                 {:db/id              db-id
                                  :user/notifications [{:notification/type          :notification.type/payout
                                                        :notification/trigger       id
                                                        :notification/acknowledged? false}]}]
                                [tx]))))
                        (apply concat)
                        (into []))
        user-cash-txs (->> user-id->total-payout
                           (map
                             (fn [[user-id {:keys [user/cash user/total-payout]}]]
                               (let [new-balance (+ cash total-payout)
                                     bailout? (> 100 new-balance)
                                     cas [:db/cas user-id :user/cash cash (if-not bailout?
                                                                            new-balance
                                                                            100N)]]
                                 (if-not bailout?
                                   [cas]
                                   [cas
                                    {:db/id              user-id
                                     :user/notifications [{:notification/type :notification.type/bailout
                                                           :notification/acknowledged? false}]}]))))
                           (apply concat)
                           (into []))]

    (d/transact (:conn datomic-cloud)
                {:tx-data (vec
                            (concat payout-txs
                                    user-cash-txs
                                    [(merge {:db/id                (:db/id proposition)
                                             :proposition/running? false
                                             :proposition/end-time processed-time
                                             :proposition/result?  result?}
                                            (when-not betting-end-time
                                              {:proposition/betting-end-time processed-time}))]))})))

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

  (let [db (d/db conn)]
    (d/pull db '[*]
            (ffirst (d/q {:query '[:find ?event ?end-time
                                   :where [?event :event/running? false]
                                   [?event :event/end-time ?end-time]]
                          :args  [db]}))))

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
