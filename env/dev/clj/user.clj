(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [whiplash.config :refer [env]]
    [clojure.spec.alpha :as s]
    [expound.alpha :as expound]
    [mount.core :as mount]
    [whiplash.db.core :as db]
    [whiplash.core :refer [start-app]]))

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(add-tap (bound-fn* clojure.pprint/pprint))

(defn start 
  "Starts application.
  You'll usually want to run this on startup."
  []
  (mount/start-without #'whiplash.core/repl-server)
  (db/add-user (:conn db/datomic-cloud)
               {:first-name "testy"
                :last-name "testerino"
                :status :user.status/active ;; don't have to verify email if we do this
                :email "test@whiplashesports.com"
                :password "11111111"
                :user-name "test"
                :verify-token "dummy"})
  )

(defn stop 
  "Stops application."
  []
  (mount/stop-except #'whiplash.core/repl-server))

(defn restart 
  "Restarts application."
  []
  (stop)
  (start))

(defn add-many-users
  [num-users]
  (doseq [x (range num-users)]
    (db/add-user (:conn db/datomic-cloud)
                 {:first-name   "testy"
                  :last-name    "testerino"
                  :status       :user.status/active ;; don't have to verify email if we do this
                  :email        (str x "@whiplashesports.com")
                  :password     "11111111"
                  :user-name    (str "test" x)
                  :verify-token "dummy"})))

(comment
  (start)
  (add-many-users 10)
  )

