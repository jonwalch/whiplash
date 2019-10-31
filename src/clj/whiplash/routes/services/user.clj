(ns whiplash.routes.services.user
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [buddy.hashers :as hashers]
            [whiplash.middleware :as middleware]
            [datomic.api :as d]))

;; https://www.regular-expressions.info/email.html
(def valid-email #"\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\b")
;; at least 8 characters or digits, max 100
(def valid-password #"^.{8,100}$")
;; at least 2 characters, max 100
(def valid-name #"^[a-zA-Z]{2,100}$")
;; anything 1 - 50
(def valid-screen-name #"^.{1,50}$")

(defn validate-user-inputs
  [{:keys [first-name last-name email password screen-name]}]
  (cond
    (not (re-matches valid-name first-name))
    "First name invalid"

    (not (re-matches valid-name last-name))
    "Last name invalid"

    (not (re-matches valid-email email))
    "Email invalid"

    (not (re-matches valid-password password))
    "Password invalid"

    (not (re-matches valid-screen-name screen-name))
    "Screen name invalid"))

;; TODO sanitize fields
(defn create-user
  [{:keys [body-params] :as req}]
  (let [{:keys [first_name last_name email password screen_name]} body-params
        encrypted-password (hashers/derive password {:alg :bcrypt+blake2b-512})
        invalid-input (validate-user-inputs {:first-name first_name
                                             :last-name last_name
                                             :email email
                                             :password password
                                             :screen-name screen_name})]
    (cond
      (some? invalid-input)
      (conflict {:message invalid-input})

      (some? (db/find-user-by-email email))
      (conflict {:message "Email taken"})

      (some? (db/find-user-by-screen-name screen_name))
      (conflict {:message "Screen name taken"})

      :else
      (do
        (db/add-user db/conn {:first-name first_name
                              :last-name  last_name
                              :status     :user.status/pending
                              :screen-name screen_name
                              :email      email
                              :password   encrypted-password})
        ;;TODO dont return 200 if db/add-user fails
        (ok {})))))

(defn get-user
  [{:keys [params] :as req}]
  (let [{:keys [user exp]} (middleware/req->token req)
        user-entity (db/find-user-by-screen-name user)]
    (if (some? user-entity)
      (ok (select-keys user-entity
                       [:user/first-name :user/last-name :user/email :user/status
                        :user/screen-name]))
      (not-found {:message (format "User %s not found" user)}))))

(defn login
  [{:keys [body-params] :as req}]
  (let [{:keys [screen_name password]} body-params
        user (db/find-user-by-screen-name screen_name)
        ;; TODO maybe return not-found if can't find user, right now just return 401
        valid-password (hashers/check password (:user/password user))
        {:keys [exp-str token]} (when valid-password
                                  (middleware/token (:user/screen-name user)))]
    (if valid-password
      {:status  200
       :headers {}
       :body {}
       ;; TODO :domain, maybe :path, maybe :secure
       :cookies {:value     token
                 :http-only true
                 :expire exp-str}}
      (unauthorized {:message "Login failed"}))))

(defn logout
  [{:keys [body-params] :as req}]
  {:status  200
   :headers {}
   :body    {}
   ;; TODO :domain, maybe :path, maybe :secure
   :cookies {:value     "deleted"
             :http-only true
             :expire    "Thu, 01 Jan 1970 00:00:00 GMT"}})

;; TODO validation
(defn create-guess
  [{:keys [body-params] :as req}]
  (let [{:keys [match_name game_id team_name team_id match_id]} body-params
        {:keys [user exp]} (middleware/req->token req)
        {:keys [user/email] :as user-entity} (db/find-user-by-screen-name user)
        existing-guess (db/find-guess (d/db db/conn) email game_id match_id)]
    (cond
      (some? existing-guess)
      (conflict {:message "Already made a guess."})

      (some? user-entity)
      (do
        (db/add-guess-for-user db/conn {:db/id     (:db/id user-entity)
                                        :game-id   game_id
                                        :match-name match_name
                                        :game-type :game.type/csgo
                                        :team-name team_name
                                        :match-id  match_id
                                        :team-id   team_id})
        (ok {}))

      :else
      (not-found {:message (format "User not found.")}))))

(defn get-guess
  [{:keys [params] :as req}]
  (let [{:keys [game_id match_id]} params
        ;; TODO figure out why this isnt getting casted by middleware
        game-id (Integer/parseInt game_id)
        match-id (Integer/parseInt match_id)
        {:keys [user exp]} (middleware/req->token req)
        existing-guess (when user
                         (db/find-guess (d/db db/conn) user game-id match-id))]
    (if (some? existing-guess)
      (ok (select-keys existing-guess
                     [:team/name :team/id :game/id :match/name :guess/time :guess/score :game/type
                      :guess/processed? :guess/processed-time]))
      (not-found {:message (format "guess for user %s, game-id %s, match-id %s not found"
                                   user
                                   game-id
                                   match-id)}))))
