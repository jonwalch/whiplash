(ns whiplash.routes.services.user
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [buddy.hashers :as hashers]
            [datomic.api :as d]
            [whiplash.middleware :as middleware]))

(defn create-user
  [{:keys [body-params] :as req}]
  (let [{:keys [first-name last-name email password]} body-params
        encrypted-password (hashers/derive password {:alg :bcrypt+blake2b-512})]
    ;; TODO sanitize fields
    ;; TODO check if user already exists for this email
    (db/add-user db/conn {:first-name first-name
                          :last-name  last-name
                          :status     :user.status/pending
                          :email      email
                          :password   encrypted-password})
    (ok)))

(defn get-user
  [{:keys [params] :as req}]
  ;; TODO sanitize email
  (if-let [user (db/find-user-by-email (:email params))]
    (ok (select-keys user
                     [:user/first-name :user/last-name :user/email :user/status]))
    (not-found)))

(defn login
  [{:keys [body-params] :as req}]
  (let [{:keys [email password]} body-params
        user (db/find-user-by-email email)
        ;; TODO maybe return not-found if can't find user, right now just return 401
        valid-password (hashers/check password (:user/password user))
        {:keys [exp-str token]} (when valid-password
                                 (middleware/token (:user/email user)))]
    (if valid-password
      {:status  200
       :headers {}
       :body    {:auth-token token}
       ;; TODO :domain, maybe :path, maybe :secure
       :cookies {:value     token
                 :http-only true
                 :expire exp-str}}
      (unauthorized))))
