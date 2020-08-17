(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [whiplash.config :refer [env]]
    [clojure.spec.alpha :as s]
    [expound.alpha :as expound]
    [mount.core :as mount]
    [whiplash.db.core :as db]
    [whiplash.core :refer [start-app]]
    [whiplash.event-manager :refer [event-manager]]
    [whiplash.routes.services.user :as user]
    [datomic.client.api :as d]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(add-tap (bound-fn* clojure.pprint/pprint))

(defn- create-and-verify-local-dev-user
  [{:keys [email user-name]}]
  (user/create-user {:body-params {:first_name "testy"
                                   :last_name  "testerino"
                                   :email      email
                                   :password   "11111111"
                                   :user_name  user-name}})
  (let [{:keys [user/verify-token db/id]} (db/pull-user {:user/email email
                                                         :attrs [:db/id :user/verify-token]})]
    (user/verify-email {:body-params {:email email
                                      :token verify-token}})
    (d/transact (:conn db/datomic-cloud)
                {:tx-data [{:db/id id
                            :user/status :user.status/admin}]})))

(defn- add-many-users
  [num-users]
  (doseq [x (range num-users)]
    (future
      (create-and-verify-local-dev-user {:email     (str "test" x "@whiplashesports.com")
                                         :user-name (str "test" x)}))))
(defn- place-bet
  [cookies]
  (user/create-prop-bet {:body-params {:bet_amount       100
                                       :projected_result (= 1 (rand-int 2))}
                         :cookies cookies}))

(defn place-bets
  [n]
  (doseq [x (range n)]
    (let [user-name (str "test" x)]
      (future
        (place-bet (-> {:body-params {:user_name user-name
                                      :password  "11111111"}}
                       (user/login)
                       :cookies))))))

(defn start
  "Starts application.
  You'll usually want to run this on startup."
  []
  (mount/start-without #'whiplash.core/repl-server)
  (create-and-verify-local-dev-user {:email "test@whiplashesports.com"
                                     :user-name "test"}))

(defn stop 
  "Stops application."
  []
  (mount/stop-except #'whiplash.core/repl-server))

(defn restart 
  "Restarts application."
  []
  (stop)
  (start))

(comment
  (start)
  (restart)
  (stop)
  (add-many-users 50)
  (place-bets 50)
  )
