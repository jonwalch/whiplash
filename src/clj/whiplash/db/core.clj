(ns whiplash.db.core
  (:require
    [datomic.client.api :as d]
    [mount.core :refer [defstate]]
    [whiplash.config :refer [env]]
    [whiplash.time :as time]
    [clojure.tools.logging :as log]
    [whiplash.db.schemas :as schemas]
    [clojure.string :as string]))

;; DO NOT USE DATOMIC ON PREM SCALAR OR COLLECTION FIND SYNTAX, IT'LL WORK LOCALLY BUT NOT IN PRODUCTION
;; https://github.com/ComputeSoftware/datomic-client-memdb#caveats

(def ^:private cloud-config
  {:server-type :cloud
   :region "us-west-2"
   :system "prod-whiplash-datomic"
   #_#_:creds-profile "<your_aws_profile_if_not_using_the_default>"
   :endpoint "http://vpce-083453c598589f6ba-s7g7d18a.vpce-svc-079c04a696f355e37.us-west-2.vpce.amazonaws.com:8182"
   ;; for local tunnel
   #_#_:endpoint "http://entry.prod-whiplash-datomic.us-west-2.datomic.net:8182"
   ;; :proxy-port is only for local tunnel my guy
   #_#_:proxy-port 8182})

(defn create-client
  [datomic-config]
  (if (:prod env)
    (do
      (log/info "using prod client with config: %s" cloud-config)
      (d/client datomic-config))
    (do
      (log/info "using dev memdb client")
      (require 'compute.datomic-client-memdb.core)
      (if-let [v (resolve 'compute.datomic-client-memdb.core/client)]
        (@v datomic-config)
        (throw (ex-info "compute.datomic-client-memdb.core is not on the classpath." {}))))))

(defn create-datomic-cloud
  []
  (let [client (create-client cloud-config)
        created? (d/create-database client {:db-name "whiplash"})
        conn (d/connect client {:db-name "whiplash"})
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

(defn show-schema
  "Show currenly installed schema"
  [conn]
  (let [system-ns #{"db" "db.type" "db.install" "db.part"
                    "db.lang" "fressian" "db.unique" "db.excise"
                    "db.cardinality" "db.fn" "db.sys" "db.bootstrap"
                    "db.alter"}]
    (d/q '[:find ?ident
           :in $ ?system-ns
           :where
           [?e :db/ident ?ident]
           [(namespace ?ident) ?ns]
           [((comp not contains?) ?system-ns ?ns)]]
         (d/db conn) system-ns)))

(defn resolve-enum
  [entity keyw]
  (when entity
    (update entity keyw #(:db/ident
                           (d/pull (d/db (:conn datomic-cloud))
                                   [:db/ident]
                                   (:db/id %))))))

(defn show-transaction
  "Show all the transaction data
   e.g.
    (-> conn show-transaction count)
    => the number of transaction"
  [conn]
  (seq (d/tx-range conn {})))

(defn add-user
  [conn {:keys [first-name last-name status email password user-name verify-token]}]
  ;; TODO :user/signup-time
  (d/transact conn {:tx-data [{:user/first-name   first-name
                               :user/last-name    last-name
                               :user/name         user-name
                               :user/status       status
                               :user/verify-token verify-token
                               :user/email        email
                               :user/password     password
                               :user/cash         (bigint 500)}]}))

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

(defn verify-email
  [conn {:keys [db/id]}]
  (d/transact conn {:tx-data [{:db/id       id
                               :user/status :user.status/active}]}))

(defn find-one-by
  [db attr val]
  (d/q {:query '[:find ?e
                 :in $ ?attr ?val
                 :where [?e ?attr ?val]]
        :args  [db attr val]}))

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

(defn find-user [id]
  (when-let [user (ffirst (find-one-by (d/db (:conn datomic-cloud)) :db/id id))]
    user))

(defn find-user-by-email
  [email]
  (when-let [user (ffirst (find-user-by-email-db (d/db (:conn datomic-cloud)) email))]
    user))

(defn find-user-by-user-name
  [user-name]
  (let [db (d/db (:conn datomic-cloud))]
    (when-let [user (ffirst (find-user-by-user-name-db db user-name))]
      user)))

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

;;; TODO resolve :game/type
(defn find-all-unprocessed-bets-for-game
  [db {:keys [game-id match-id]}]
  (->> (d/q
         {:query '[:find ?bet
                   :in $ ?game-id ?match-id
                   :where [?bet :bet/processed? false]
                   [?bet :match/id ?match-id]
                   [?bet :game/id ?game-id]]
          :args  [db game-id match-id]})
       (map first)
       (map #(d/pull db '[:bet/amount :user/_bets :team/name :game/type] %))
       (mapv (fn [bet]
              (-> bet
                  (merge (d/pull db
                                 '[:user/name]
                                 (-> bet :user/_bets :db/id)))
                  ;; for now dissoc game/type
                  (dissoc :user/_bets :game/type))))))

(defn find-all-unprocessed-bets
  [db]
  (->> (d/q {:query '[:find ?bet
                      :where [?bet :bet/processed? false]]
             :args  [db]})
       (mapv
         ;;each guess is a vector with 1 element
         (fn [bet]
           (d/pull db
                   '[:game/id :bet/processed? :bet/amount :match/id
                     :db/id :game/type :user/_bets :team/id]
                   (first bet))))))

(defn find-this-week-payout-leaderboard
  [lower-bound]
  (let [db (d/db (:conn datomic-cloud))]
    (->> (d/q
           {:query '[:find ?bet
                     :in $ ?lower-bound
                     :where [?bet :bet/time ?time]
                     [?bet :bet/payout ?payout]
                     [(>= ?time ?lower-bound)]
                     [?bet :bet/processed? true]
                     [(> ?payout 0)]]
            :args  [db lower-bound]})
         (map #(d/pull db '[:bet/payout :user/_bets] (first %)))
         (map (fn [bet]
                (merge bet
                       (d/pull db
                               '[:user/name]
                               (-> bet :user/_bets :db/id))))))))

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
         (sort-by :cash #(compare %2 %1))
         (vec))))

(defn find-top-ten
  []
  (->> (find-all-time-leaderboard)
       (take 10)
       vec))

(comment

  (defn find-loser-by-email
    [email conn]
    (when-let [user (ffirst (find-user-by-email-db (d/db conn) email))]
      user))

  (defn make-admin
    [email conn]
    (let [user (find-loser-by-email email conn)]
      (d/transact conn {:tx-data [{:db/id       user
                                   :user/status :user.status/admin}]})))

  (def test-client (d/client cloud-config))
  ;(d/create-database test-client {:db-name "test"})
  (def conn (d/connect test-client {:db-name "whiplash"}))
  (d/transact conn {:tx-data (schemas/migrations->schema-tx)})

  (find-loser-by-email "foobar@whiplashesports.com" conn)
  (make-admin "foobar@whiplashesports.com" conn)


  (d/transact conn {:tx-data [{:user/first-name  "testy"
                               :user/last-name "testerino"
                               :user/bets   []}]})

  (d/delete-database test-client {:db-name "foo"}))
