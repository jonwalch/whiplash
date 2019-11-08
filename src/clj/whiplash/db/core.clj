(ns whiplash.db.core
  (:require
    [datomic.client.api :as d]
    [mount.core :refer [defstate]]
    [whiplash.config :refer [env]]
    [whiplash.time :as time]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]))

;; DO NOT USE DATOMIC ON PREM SCALAR OR COLLECTION FIND SYNTAX, IT'LL WORK LOCALLY BUT NOT IN PRODUCTION
;; https://github.com/ComputeSoftware/datomic-client-memdb#caveats

(def ^:private cloud-config
  {:server-type :cloud
   :region "us-west-2"
   :system "prod-whiplash-datomic"
   #_#_:creds-profile "<your_aws_profile_if_not_using_the_default>"
   :endpoint "http://vpce-083453c598589f6ba-s7g7d18a.vpce-svc-079c04a696f355e37.us-west-2.vpce.amazonaws.com:8182"
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

(defn- migration-files->txes
  []
  (->> (io/file "./resources/migrations")
       (file-seq)
       (filter #(.isFile %))
       (mapv (comp edn/read-string slurp))
       flatten
       vec))

(defn create-datomic-cloud
  []
  (let [client (create-client cloud-config)
        created? (d/create-database client {:db-name "whiplash"})
        conn (d/connect client {:db-name "whiplash"})
        schema-tx-result (d/transact conn {:tx-data (migration-files->txes)})]
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
                               :user/password     password}]}))

(defn add-guess-for-user
  "purposely leaving out :guess/score, will be added by another piece of code later"
  [conn {:keys [db/id game-type match-name game-id team-name team-id match-id]}]
  (d/transact conn {:tx-data [{:db/id        id
                               :user/guesses [{:guess/time       (time/to-date)
                                               :guess/processed? false
                                               :game/type        game-type
                                               :match/name       match-name
                                               :game/id          game-id
                                               :team/name        team-name
                                               :team/id          team-id
                                               :match/id         match-id}]}]}))

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

(defn find-user [id]
  (when-let [user (ffirst (find-one-by (d/db (:conn datomic-cloud)) :db/id id))]
    user))

(defn find-user-by-email [email]
  (when-let [user (ffirst (find-one-by (d/db (:conn datomic-cloud)) :user/email email))]
     user))

(defn find-user-by-user-name [user-name]
  (let [db (d/db (:conn datomic-cloud))]
    (when-let [user (ffirst (find-one-by db :user/name user-name))]
      user)))

(defn find-guess
  [db user-name game-id match-id]
  (ffirst (d/q {:query '[:find ?guess
                         :in $ ?user-name ?game-id ?match-id
                         :where [?user :user/name ?user-name]
                         [?user :user/guesses ?guess]
                         [?guess :game/id ?game-id]
                         [?guess :match/id ?match-id]]
                :args [db user-name game-id match-id]})))

(defn find-all-unprocessed-guesses
  []
  (let [db (d/db (:conn datomic-cloud))]
    (->> (d/q {:query '[:find ?guess
                        :where [?guess :guess/processed? false]]
               :args  [db]})
         (mapv
           ;;each guess is a vector with 1 element
           (fn [guess]
             (d/pull db
                     '[*]
                     (first guess)))))))

(defn find-this-week-leaderboard
  [lower-bound]
  (let [db (d/db (:conn datomic-cloud))]
    (->> (d/q
        {:query '[:find ?guess
                  :in $ ?lower-bound
                  :where [?guess :guess/time ?time]
                  [?guess :guess/score ?score]
                  [(>= ?time ?lower-bound)]
                  [?guess :guess/processed? true]
                  [(> ?score 0)]]
         :args  [db lower-bound]})
      (map #(d/pull db '[:guess/score :user/_guesses] (first %)))
      (mapv (fn [guess]
             (-> guess
                 (assoc :user/name
                        (:user/name (d/pull db
                                            '[:user/name]
                                            (-> guess :user/_guesses :db/id))))))))))

(comment
  (def test-client (d/client cloud-config))
  (d/create-database test-client {:db-name "test"})
  (def conn (d/connect test-client {:db-name "test"}))
  ;(install-schema conn)


  (d/transact conn {:tx-data (migration-files->txes)})

  (d/transact conn {:tx-data [{:user/first-name  "testy"
                               :user/last-name "testerino"
                               :user/guesses   []}]})

  (d/delete-database test-client {:db-name "foo"}))
