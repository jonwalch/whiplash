(ns whiplash.routes.services.user
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [buddy.hashers :as hashers]
            [whiplash.middleware :as middleware]
            [datomic.client.api :as d]
            [whiplash.integrations.amazon-ses :as ses]
            [clojure.tools.logging :as log])
  (:import (java.security MessageDigest)))

;; https://www.regular-expressions.info/email.html
(def valid-email #"\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\b")
;; at least 8 characters or digits, max 100
(def valid-password #"^.{8,100}$")
;; at least 2 characters, max 100
(def valid-name #"^[a-zA-Z ,.'-]{1,100}$")
;; anything 1 - 50
(def valid-user-name #"^.{1,50}$")

(defn validate-user-inputs
  [{:keys [first-name last-name email password user-name]}]
  (cond
    (not (re-matches valid-name first-name))
    "First name invalid"

    (not (re-matches valid-name last-name))
    "Last name invalid"

    (not (re-matches valid-email email))
    "Email invalid"

    (not (re-matches valid-password password))
    "Password invalid"

    (not (re-matches valid-user-name user-name))
    "User name invalid"))

(defn md5 [^String s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn verify-email-token
  []
  (md5 (rand-str 100)))

;; TODO sanitize fields
(defn create-user
  [{:keys [body-params] :as req}]
  (let [{:keys [first_name last_name email password user_name]} body-params
        encrypted-password (hashers/derive password {:alg :bcrypt+blake2b-512})
        invalid-input (validate-user-inputs {:first-name first_name
                                             :last-name last_name
                                             :email email
                                             :password password
                                             :user-name user_name})
        email-token (verify-email-token)]
    (cond
      (some? invalid-input)
      (conflict {:message invalid-input})

      (some? (db/find-user-by-email email))
      (conflict {:message "Email taken"})

      (some? (db/find-user-by-user-name user_name))
      (conflict {:message "User name taken"})

      :else
      (let [tx-result (db/add-user (:conn db/datomic-cloud)
                                   {:first-name   first_name
                                    :last-name    last_name
                                    :status       :user.status/pending
                                    :verify-token email-token
                                    :user-name    user_name
                                    :email        email
                                    :password     encrypted-password})]
        (ses/send-verification-email {:user/first-name first_name
                                      :user/verify-token email-token
                                      :user/email email})
        ;;TODO dont return 200 if db/add-user fails
        (ok {})))))

(defn get-user
  [{:keys [params] :as req}]
  (let [{:keys [user exp]} (middleware/req->token req)
        user-entity (-> (d/pull (d/db (:conn db/datomic-cloud))
                                '[:user/first-name :user/last-name :user/email :user/status
                                  :user/name :user/verify-token :user/cash]
                                (db/find-user-by-user-name user))
                        (db/resolve-enum :user/status))]
    (if (some? user-entity)
      ;; TODO don't return verify-token, currently only exposing it for testing purposes
      ;; A user could falsely verify their email if they poked around and reconstructed the
      ;; correct route and query params
      (ok user-entity)
      (not-found {:message (format "User %s not found" user)}))))

(defn login
  [{:keys [body-params] :as req}]
  (let [{:keys [user_name password]} body-params
        found-user (db/find-user-by-user-name user_name)
        user-entity (when found-user
                      (d/pull (d/db (:conn db/datomic-cloud))
                              '[:user/password :user/name]
                              found-user))
        ;; TODO maybe return not-found if can't find user, right now just return 401
        valid-password (hashers/check password (:user/password user-entity))
        {:keys [exp-str token]} (when valid-password
                                  (middleware/token (:user/name user-entity)))]
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
(defn create-bet
  [{:keys [body-params] :as req}]
  (let [{:keys [match_name game_id team_name team_id match_id bet_amount]} body-params
        {:keys [user exp]} (middleware/req->token req)
        db (d/db (:conn db/datomic-cloud))
        {:keys [db/id user/cash]} (d/pull db '[:user/cash :db/id] (db/find-user-by-user-name user))]
    (cond
      (>= 0 bet_amount)
      (conflict {:message "Cannot bet less than 1."})

      (> bet_amount cash)
      (conflict {:message "Bet cannot exceed total user cash."})

      (some? id)
      (if (try
            (db/add-guess-for-user (:conn db/datomic-cloud)
                                   {:db/id      id
                                    :game-id    game_id
                                    :match-name match_name
                                    :game-type  :game.type/csgo
                                    :team-name  team_name
                                    :match-id   match_id
                                    :team-id    team_id
                                    :bet-amount bet_amount
                                    :cash       cash})
            (catch Throwable t (log/info "cas failed for adding guess: " t)))
        (ok {})
        (conflict {:message "CAS failed"}))

      :else
      (not-found {:message "User not found."}))))

;; TODO there may now be more than 1 bet, so we need to return them all
(defn get-bet
  [{:keys [params] :as req}]
  (let [{:keys [game_id match_id]} params
        ;; TODO figure out why this isnt getting casted by middleware
        game-id (Integer/parseInt game_id)
        match-id (Integer/parseInt match_id)
        {:keys [user exp]} (middleware/req->token req)
        db (d/db (:conn db/datomic-cloud))
        existing-guess (when user
                         (db/find-bet db user game-id match-id))
        pulled-guess (when existing-guess
                       (-> (d/pull db '[*] existing-guess)
                           (db/resolve-enum :game/type)))]
    (if (some? pulled-guess)
      (ok pulled-guess)
      (not-found {:message (format "guess for user %s, game-id %s, match-id %s not found"
                                   user
                                   game-id
                                   match-id)}))))

(defn verify-email
  [{:keys [body-params] :as req}]
  (let [{:keys [email token]} body-params
        {:keys [db/id user/verify-token user/status] :as user} (-> (d/pull (d/db (:conn db/datomic-cloud))
                                                                           '[:db/id :user/status :user/verify-token]
                                                                           (db/find-user-by-email email))
                                                                   (db/resolve-enum :user/status))]
    (cond
      (and (= token verify-token)
           (= :user.status/pending status))
      ;;TODO dont return 200 if db/verify-email fails
      (do
        (db/verify-email (:conn db/datomic-cloud) {:db/id id})
        (ok {:message (format "Successfully verified %s" email)}))

      (= token verify-token)
      (ok {:message (format "Already verified %s" email)})

      :else
      (not-found {:message (format "Couldn't verify %s" email)}))))
