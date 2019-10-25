(ns whiplash.routes.services.user
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [buddy.hashers :as hashers]
            [whiplash.middleware :as middleware]
            [datomic.api :as d]))

;; TODO sanitize fields
(defn create-user
  [{:keys [body-params] :as req}]
  (let [{:keys [first_name last_name email password screen_name]} body-params
        encrypted-password (hashers/derive password {:alg :bcrypt+blake2b-512})]
    (cond
      (some? (db/find-user-by-email email))
      (conflict {:message "email taken"})

      (some? (db/find-user-by-screen-name screen_name))
      (conflict {:message "screen name taken"})

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
        user-entity (db/find-user-by-email user)]
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
                                  (middleware/token (:user/email user)))]
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
        {:keys [user/email] :as user-entity} (db/find-user-by-email user)
        existing-guess (db/find-guess (d/db db/conn) email game_id match_id)
        ]
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

(comment
  (let [{:keys [user exp]} (middleware/req->token sheet)]
    user))

;; TODO assert on match-id and game-id
(defn get-guess
  [{:keys [params] :as req}]
  (let [{:keys [game_id match_id]} params
        ;; TODO figure out why this isnt getting casted by middleware
        game-id (Integer/parseInt game_id)
        match-id (Integer/parseInt match_id)
        {:keys [user exp]} (middleware/req->token req)
        {:keys [user/email] :as user-entity} (db/find-user-by-email user)
        _ (println email game-id match-id)
        existing-guess (db/find-guess (d/db db/conn) email game-id match-id)]
    (if (some? existing-guess)
      (ok (select-keys existing-guess
                     [:team/name :team/id :game/id :match/name :guess/time :guess/score :game/type]))
      (not-found {:message (format "guess for user %s, game-id %s, match-id %s not found"
                                   email
                                   game-id
                                   match-id)}))))
