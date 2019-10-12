(ns whiplash.routes.services.user
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [buddy.hashers :as hashers]
            [whiplash.middleware :as middleware]
            [datomic.api :as d]))

(defn create-user
  [{:keys [body-params] :as req}]
  (let [{:keys [first-name last-name email password screen-name]} body-params
        encrypted-password (hashers/derive password {:alg :bcrypt+blake2b-512})]
    ;; TODO sanitize fields
    ;; TODO check if user already exists for this email or screen-name
    (db/add-user db/conn {:first-name first-name
                          :last-name  last-name
                          :status     :user.status/pending
                          :screen-name screen-name
                          :email      email
                          :password   encrypted-password})
    (ok)))

(defn get-user
  [{:keys [params] :as req}]
  ;; TODO sanitize email
  (if-let [user (db/find-user-by-email (:email params))]
    (ok (select-keys user
                     [:user/first-name :user/last-name :user/email :user/status
                      :user/screen-name]))
    (not-found {:message (format "User %s not found" (:email params))})))

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

;; TODO lookup if they already have a guess for this combo
;; TODO actually use game-type
;; TODO validation
(defn create-guess
  [{:keys [body-params] :as req}]
  (let [{:keys [screen-name game-type game-name game-id team-name team-id]} body-params
        user (db/find-user-by-screen-name screen-name)]
    (if user
      (do
        (db/add-guess-for-user db/conn {:db/id     (:db/id user)
                                        :game-id   game-id
                                        :game-name game-name
                                        :game-type :game.type/csgo
                                        :team-name team-name
                                        :team-id   team-id})
        (ok))
      (not-found {:message (format "User %s not found" screen-name)}))))

;; TODO assert on screen-anme and game-id
(defn get-guess
  [{:keys [params] :as req}]
  (let [{:keys [screen-name game-id]} params
        ;; TODO figur eout why this isnt getting casted by middleware
        game-id (Integer/parseInt game-id)]
    (if-let [guess (db/find-guess-for-game-id (d/db db/conn) screen-name game-id)]
      (ok (select-keys guess
                     [:team/name :team/id :game/id :game/name :guess/time :guess/score :game/type]))
      (not-found {:message (format "guess for user %s, game-id %s not found"
                                   screen-name
                                   game-id)}))))

(comment (Integer/parseInt "123"))
#_(select-keys guess
               [:team/name :team/id :game/id :game/name :guess/time :guess/score :game/type])