(ns whiplash.routes.services.user
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [clj-uuid :as uuid]
            [buddy.hashers :as hashers]
            [datomic.api :as d]))

(defn create-user
  [{:keys [body-params] :as req}]
  (let [{:keys [first-name last-name email password]} body-params
        encrypted-password (hashers/derive password {:alg :bcrypt+blake2b-512})
        #_(println (hashers/check "foobar" encrypted-password))
        ]
    ;; TODO sanitize fields
    (db/add-user db/conn {:id         (uuid/v4)
                          :first-name first-name
                          :last-name  last-name
                          :status     :user.status/pending
                          :email      email
                          :password   encrypted-password})
    (ok)))

(defn get-user
  [{:keys [params] :as req}]
  ;; TODO sanitize email
  (let [entity (-> (d/db db/conn)
                   (db/find-user-by-email (:email params))
                   d/touch)]
    (ok (select-keys entity
                     [:user/first-name :user/last-name :user/email :user/status])))
  )
