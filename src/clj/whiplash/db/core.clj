(ns whiplash.db.core
  (:require
    [datomic.api :as d]
    [io.rkn.conformity :as c]
    [mount.core :refer [defstate]]
    [whiplash.config :refer [env]]
    [clj-uuid :as uuid]))

(defn install-schema
  "This function expected to be called at system start up.

  Datomic schema migrations or db preinstalled data can be put into 'migrations/schema.edn'
  Every txes will be executed exactly once no matter how many times system restart."
  [conn]
  (let [norms-map (c/read-resource "migrations/schema.edn")]
    (c/ensure-conforms conn norms-map (keys norms-map))))

(defstate conn
  :start (let [database-url (:database-url env)
               created? (d/create-database database-url)
               conn (d/connect database-url)]
           (install-schema conn)
           conn)
  :stop (-> conn .release))

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

(defn show-transaction
  "Show all the transaction data
   e.g.
    (-> conn show-transaction count)
    => the number of transaction"
  [conn]
  (seq (d/tx-range (d/log conn) nil nil)))

;; TODO validate args
(defn add-user
  [conn {:keys [id first-name last-name status email password]}]
  @(d/transact conn [{:user/id         id
                      :user/first-name first-name
                      :user/last-name  last-name
                      :user/status     status
                      :user/email      email
                      :user/password   password}]))

(defn find-one-by
  "Given db value and an (attr/val), return the user as EntityMap (datomic.query.EntityMap)
   If there is no result, return nil.

   e.g.
    (d/touch (find-one-by (d/db conn) :user/email \"user@example.com\"))
    => show all fields
    (:user/first-name (find-one-by (d/db conn) :user/email \"user@example.com\"))
    => show first-name field"
  [db attr val]
  (d/entity db
            ;;find Specifications using ':find ?a .' will return single scalar
            (d/q '[:find ?e .
                   :in $ ?attr ?val
                   :where [?e ?attr ?val]]
                 db attr val)))

(defn find-user [db uuid]
  (when-let [user (find-one-by db :user/id uuid)]
    (d/touch user)))

(defn find-user-by-email [db email]
  (when-let [user (find-one-by db :user/email email)]
    (d/touch user)))

(comment
  (def test-uuid #uuid"c0e83a90-8d64-441c-863b-43dbc9369277")
  (find-user (d/db conn) test-uuid))

