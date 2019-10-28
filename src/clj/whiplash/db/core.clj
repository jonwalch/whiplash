(ns whiplash.db.core
  (:require
    [datomic.api :as d]
    [io.rkn.conformity :as c]
    [mount.core :refer [defstate]]
    [whiplash.config :refer [env]]
    [whiplash.time :as time]))

(defn install-schema
  "This function expected to be called at system start up.

  Datomic schema migrations or db preinstalled data can be put into 'migrations/schema.edn'
  Every txes will be executed exactly once no matter how many times system restart."
  [conn]
  (let [norms-map (c/read-resource "migrations/schema.edn")]
    (c/ensure-conforms conn norms-map (keys norms-map))))

(defstate conn
  :start (let [database-url (:database-url env)
               _ (assert (some? database-url))
               created? (d/create-database database-url)
               conn (d/connect database-url)]
           (install-schema conn)
           conn)
  :stop (some-> conn .release))

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
  [conn {:keys [first-name last-name status email password screen-name]}]
  ;; TODO :user/signup-time
  @(d/transact conn [{:user/first-name first-name
                      :user/last-name  last-name
                      :user/screen-name screen-name
                      :user/status     status
                      :user/email      email
                      :user/password   password}]))

(defn add-guess-for-user
  "purposely leaving out :guess/score, will be added by another piece of code later"
  [conn {:keys [db/id game-type match-name game-id team-name team-id match-id]}]
  ;; TODO, need match/id as well
  @(d/transact conn [{:db/id        id
                      :user/guesses [{:guess/time (time/to-date)
                                      :guess/processed? false
                                      :game/type  game-type
                                      :match/name  match-name
                                      :game/id    game-id
                                      :team/name  team-name
                                      :team/id    team-id
                                      :match/id match-id}]}]))

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

;; TODO don't touch these unless necessary, we're pulling in all of :user/guesses too
(defn find-user [id]
  (when-let [user (find-one-by (d/db conn) :db/id id)]
    (d/touch user)))

(defn find-user-by-email [email]
  (when-let [user (find-one-by (d/db conn) :user/email email)]
    (d/touch user)))

(defn find-user-by-screen-name [screen-name]
  (when-let [user (find-one-by (d/db conn) :user/screen-name screen-name)]
    (d/touch user)))

;(defn find-newest-guess
;  [screen-name]
;  (when-let [user (find-user-by-screen-name screen-name)]
;    (->> user
;        :user/guesses
;        (sort-by :guess/time #(compare %2 %1))
;        first)))
;
(defn find-guess
  [db email game-id match-id]
  (when-let [guess (d/entity db
                             (d/q
                               '[:find ?guess .
                                 :in $ ?email ?game-id ?match-id
                                 :where [?user :user/email ?email]
                                 [?user :user/guesses ?guess]
                                 [?guess :game/id ?game-id]
                                 [?guess :match/id ?match-id]]
                               db email game-id match-id))]
    (d/touch guess)))

(defn find-all-unprocessed-guesses
  []
  (let [db (d/db conn)]
    (->> db
         (d/q
           '[:find [?guess ...]
             :where [?guess :guess/processed? false]])
         (mapv (fn [id]
                (d/touch
                  (d/entity db id)))))))

(defn find-this-week-leaderboard-calc
  [lower-bound]
  (let [db (d/db conn)]
    (->> (d/q
           '[:find [?guess ...]
             :in $ ?lower-bound
             :where [?guess :guess/time ?time]
             [?guess :guess/score ?score]
             [(>= ?time ?lower-bound)]
             [?guess :guess/processed? true]
             [(> ?score 0)]]
           db lower-bound)
         (mapv (fn [id]
                 (d/touch
                   (d/entity db id)))))))
