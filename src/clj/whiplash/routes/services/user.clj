(ns whiplash.routes.services.user
  (:require [ring.util.http-response :refer :all]
            [whiplash.db.core :as db]
            [buddy.hashers :as hashers]
            [whiplash.middleware :as middleware]
            [datomic.client.api :as d]
            [datomic.client.api.async :as d.async]
            [whiplash.integrations.amazon-ses :as ses]
            [clojure.tools.logging :as log]
            [whiplash.time :as time]
            [java-time :as jtime]
            [clojure.string :as string]
            [whiplash.constants :as constants]
            [buddy.core.mac :as mac]
            [buddy.core.codecs :as codecs]
            [clojure.core.async :as async])
  (:import (java.security MessageDigest)))

;; https://www.regular-expressions.info/email.html
(def valid-email #"\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\b")
;; at least 8 characters or digits, max 100
(def valid-password #"^.{8,100}$")
;; at least 2 characters, max 100
(def valid-name #"^[a-zA-Z ,.'-]{1,100}$")
;; anything 1 - 50
(def valid-user-name #"^[^ ]{1,50}$")
;; unauthed user format
(def unauth-user-name #"(?i)user-[a-zA-Z]{21}")

(def invalid-password "Password must be at least 8 characters")

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
    invalid-password

    (or (not (re-matches valid-user-name user-name))
        ;; user name should not be of email format
        (re-matches valid-email user-name)
        (re-matches unauth-user-name user-name))
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

(defn- hash-password
  [password]
  (hashers/derive password {:alg :bcrypt+blake2b-512}))

;; I picked the letters here arbitrarily
(def ^:private fuzz-map
  {\0 "z" \1 "c" \2 "A" \3 "g" \4 "w" \5 "P" \6 "x" \7 "N" \8 "v" \9 "y"})

;; user-ccPxgcAwcwNcPvNNxcywg
#_(defn- unauthed-username
  [{:keys [cookies]}]
  (when-let [ga (:value (get cookies "_ga"))]
    (format "user-%s"
            (->> (string/replace ga #"[a-zA-Z]|\." "")
                 (map #(get fuzz-map %))
                 (apply str)))))

(defn- twitch-unauth-username
  [{:keys [headers]}]
  (when-let [oid (get headers "x-twitch-opaque-id")]
    (format "user-%s"
            (->> (string/replace oid #"\." "")
                 (map #(or (get fuzz-map %)
                           ;; if its a character just use the character
                           %))
                 (apply str)))))

(defn create-user
  [{:keys [body-params] :as req}]
  (let [{:keys [first_name last_name email password user_name]} body-params
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
      (let [encrypted-password (hash-password password)
            tx-result (db/add-user {:first-name   first_name
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

(defn update-password
  [{:keys [body-params] :as req}]
  (let [{:keys [password]} body-params
        invalid-input (when-not (re-matches valid-password password)
                        invalid-password)]
    (cond
      (some? invalid-input)
      (conflict {:message invalid-input})

      :else
      (let [{:keys [user exp]} (middleware/req->token req)
            encrypted-password (hash-password password)
            tx-result (db/update-password (:conn db/datomic-cloud)
                                          {:db/id (db/find-user-by-user-name user)
                                           :password     encrypted-password})]
        ;;TODO dont return 200 if db/update-password fails
        (ok {})))))

(defn- ack-user-notifications
  [{:keys [db/id user/unacked-notifications]}]
  ;; TODO: maybe filter nofications by type, right now we want them all to be returned from this
  (let [notifications (map (fn [notif]
                             (update notif :notification/type :db/ident))
                           unacked-notifications)
        ;; ack notifications, dubious because this makes this GET no longer idempotent
        ack-tx (when-not (empty? notifications)
                 (let [ack-time (time/to-date)]
                   ;; TODO move this tx into db.core
                   (d.async/transact
                     (:conn db/datomic-cloud)
                     {:tx-data (into []
                                     (concat
                                       (map (fn [{:keys [db/id]}]
                                              [:db/retractEntity id])
                                            notifications)
                                       (list
                                         {:db/id id
                                          :user/acked-notifications
                                                 (mapv (fn [{:keys [notification/trigger notification/type]}]
                                                         (merge
                                                           {:notification/type              type
                                                            :notification/acknowledged-time ack-time}
                                                           (when trigger
                                                             {:notification/trigger (:db/id trigger)})))
                                                       notifications)})))})))]
    (map
      (comp
        (fn [{:keys [notification/type] :as munged-notif}]
          (if (or (= :notification.type/bailout type)
                  (= :notification.type/no-bailout type))
            (dissoc munged-notif :proposition/text :bet/payout :proposition/result)
            munged-notif))
        ;; munge db results
        (fn [{:keys [notification/trigger] :as notif}]
          (-> notif
              (assoc :bet/payout (:bet/payout trigger)
                     :proposition/text (get-in trigger [:bet/proposition :proposition/text])
                     :proposition/result (get-in trigger [:bet/proposition :proposition/result :db/ident]))
              (dissoc :notification/trigger :db/id))))
      notifications)))

;; TODO: revisit when we let users change their usernames because that will create
;; them as a separate user in full story
(defn- user-id-hmac
  "This is used to identify users across fullstory sessions. Shouldn't be rotated."
  [{:keys [user/name]}]
  (-> (mac/hash name {:key "dd6f3c930d84dc5930280d3d110e9249" :alg :hmac+sha256})
      (codecs/bytes->hex)))

(defn get-user
  [{:keys [params] :as req}]
  (let [{:keys [user exp]} (middleware/req->token req)
        db (d/db (:conn db/datomic-cloud))
        user-name (or user
                      (twitch-unauth-username req)
                      #_(unauthed-username req))
        valid-un? (and (string? user-name)
                       (not (empty? user-name)))
        prop-count-chan (when (and valid-un? (string/starts-with? user-name "user-"))
                          (db/user-prop-count {:user/name user-name}))
        user-entity (when valid-un?
                      (db/pull-user
                        {:user/name user-name
                         :db        db
                         :attrs     [{:user/status [:db/ident]}
                                     :user/first-name :user/last-name :user/email
                                     :user/name :user/cash :db/id
                                     {:user/unacked-notifications
                                      [:db/id {:notification/type [:db/ident]}
                                       {:notification/trigger
                                        [:bet/payout
                                         :db/id
                                         {:bet/proposition
                                          [:db/id
                                           :proposition/text
                                           {:proposition/result [:db/ident]}]}]}]}]}))]
    (if (some? user-entity)
      (do
        {:status  200
         :headers constants/CORS-GET-headers
         :body    (-> user-entity
                      (dissoc :db/id :user/unacked-notifications)
                      (assoc :user/notifications (ack-user-notifications user-entity)
                             :user/id (user-id-hmac user-entity)
                             :user/gated? (>= (or (when prop-count-chan
                                                    (ffirst
                                                      (async/<!! prop-count-chan)))
                                                  0)
                                              10)))})
      {:status  404
       :headers constants/CORS-GET-headers
       :body    {:message (format "User %s not found" user)}})))

(defn login
  [{:keys [body-params] :as req}]
  (let [{:keys [user_name password]} body-params
        attrs [:user/password :user/name {:user/status [:db/ident]}]
        db (d/db (:conn db/datomic-cloud))
        ;; try to find user by their user name or their email
        ;; client will pass as user_name either way, we don't allow users to have user names
        ;; that are of email address format, so we won't ever pull the wrong user
        user-entity (or (db/pull-user {:user/name user_name
                                       :db db
                                       :attrs     attrs})
                        (db/pull-user {:user/email user_name
                                       :db db
                                       :attrs attrs}))
        valid-password (when user-entity
                         (hashers/check password (:user/password user-entity)))
        {:keys [exp-str token]} (when valid-password
                                  (middleware/token (:user/name user-entity)
                                                    (:user/status user-entity)))]
    (if valid-password
      {:status  200
       :headers {}
       :body    {}
       ;; TODO :secure
       :cookies {"value"
                 {:value     token
                  :path      "/"
                  :http-only true
                  :expires    exp-str
                  :same-site :strict}}}
      (unauthorized {:message "Invalid user name or password"}))))

(defn logout
  [{:keys [body-params] :as req}]
  {:status  200
   :headers {}
   :body    {}
   ;; TODO :secure
   :cookies {"value" {:value     "deleted"
                      :path "/"
                      :http-only true
                      :expires    "Thu, 01 Jan 1970 00:00:00 GMT"
                      :same-site :strict}}})

(defn- pull-and-maybe-create-user
  [{:keys [user twitch-ext-unauth-user db]}]
  (let [pulled-user (db/pull-user {:db        db
                                   :user/name (or user twitch-ext-unauth-user)
                                   :attrs     [:user/cash :db/id {:user/status [:db/ident]}]})]
    (cond
      pulled-user
      pulled-user

      twitch-ext-unauth-user
      (let [tx-result (db/create-unauthed-user twitch-ext-unauth-user :user.status/twitch-ext-unauth)]
        (db/pull-user {:db (:db-after tx-result)
                       :user/name twitch-ext-unauth-user
                       :attrs     [:user/cash :db/id {:user/status [:db/ident]}]}))
      #_unauthed-user
      #_(let [tx-result (db/create-unauthed-user unauthed-user :user.status/twitch-ext-unauth)]
        (db/pull-user {:db (:db-after tx-result)
                       :user/name unauthed-user
                       :attrs     [:user/cash :db/id {:user/status [:db/ident]}]})))))

(defn create-prop-bet
  [{:keys [body-params] :as req}]
  (let [{:keys [bet_amount projected_result]} body-params
        {:keys [user exp]} (middleware/req->token req)
        twitch-ext-unauth-user (when-not user
                                 (twitch-unauth-username req))
        #_#_unauthed-user (when (and (not user)
                                 #_(not twitch-ext-unauth-user))
                        (unauthed-username req))
        db (d/db (:conn db/datomic-cloud))
        {:keys [db/id user/cash user/status]} (pull-and-maybe-create-user
                                                {:user user
                                                 #_#_:unauthed-user unauthed-user
                                                 :twitch-ext-unauth-user twitch-ext-unauth-user
                                                 :db db})
        ongoing-prop (db/pull-ongoing-proposition {:db db :attrs [:db/id]})
        ;; TODO: make this query part of pulling the user if it shaves off a query
        existing-bet (when (and id ongoing-prop)
                       (db/find-existing-prop-bet {:db                    db
                                                   :user-id               id
                                                   :prop-id               (:db/id ongoing-prop)
                                                   :bet/projected-result? projected_result}))]
    (cond
      #_(not-any? #(= % status) [:user.status/active :user.status/admin
                               :user.status/unauth :user.status/twitch-ext-unauth])
      #_(conflict {:message "User not in betable state."})

      (and (some? existing-bet) (>= 0 bet_amount))
      (conflict {:message "You must bet more than 0."})

      (and (nil? existing-bet) (> 100 bet_amount))
      (conflict {:message "You must bet 100 or more!"})

      (> bet_amount cash)
      (conflict {:message "Bet cannot exceed total user cash."})

      (nil? (:db/id ongoing-prop))
      (method-not-allowed {:message "No ongoing prop bet, cannot make bet."})

      (try
        (jtime/after? (time/now)
                      ;; Doing the query again to make sure that betting hasn't closed since we grabbed the last db
                      (time/date-to-zdt (:proposition/betting-end-time
                                          (db/pull-ongoing-proposition
                                            {:attrs [:proposition/betting-end-time]}))))
        ;; Catch the NPE in case :proposition/betting-end-time is nil because no ongoing prop was found
        ;; Default to true because we want to hit method not allowed if no ongoing prop
        (catch NullPointerException e true))
      (method-not-allowed {:message "Betting for proposition has ended."})

      (some? id)
      (if existing-bet
        (if (try
              (db/update-prop-bet-amount existing-bet cash id bet_amount)
              (catch Throwable t (log/error "cas failed for adding prop bet: " t)))
          (ok {})
          (conflict {:message "CAS failed"}))
        (if (try
              (db/add-prop-bet-for-user (:conn db/datomic-cloud)
                                        {:db/id                 id
                                         :bet/projected-result? projected_result
                                         :bet/amount            bet_amount
                                         :user/cash             cash
                                         :bet/proposition       (:db/id ongoing-prop)})
              (catch Throwable t (log/error "cas failed for adding prop bet: " t)))
          (ok {})
          (conflict {:message "CAS failed"})))

      :else
      (not-found {:message "User not found."}))))

(defn create-suggestion
  [{:keys [body-params] :as req}]
  (let [{:keys [text]} body-params
        {:keys [user]} (middleware/req->token req)
        db (d/db (:conn db/datomic-cloud))
        {:keys [db/id user/status]} (when user
                                      (db/pull-user {:db        db
                                                     :user/name user
                                                     :attrs     [:db/id {:user/status [:db/ident]}]}))
        ongoing-event (db/find-ongoing-event db)]

    (cond
      (not-any? #(= % status) [:user.status/active :user.status/admin])
      (method-not-allowed {:message "Must have email verified to suggest."})

      (nil? ongoing-event)
      (method-not-allowed {:message "No ongoing event, cannot make suggestion"})

      (empty? text)
      (method-not-allowed {:message "Invalid text"})

      (nil? id)
      (not-found {:message "User not found"})

      (> (count text) 100)
      (method-not-allowed {:message "Text is too long"})

      (some? ongoing-event)
      (if (try
            (db/add-user-suggestion-to-event {:event-eid ongoing-event
                                              :text text
                                              :user-eid id})
            (catch Throwable t (log/info "transaction failed: " t)))
        (ok {})
        (conflict {:message "Transaction failed"}))

      :else
      (not-found {:message ""}))))

(defn get-prop-bets
  [{:keys [params] :as req}]
  (let [{:keys [user exp]} (middleware/req->token req)
        db (d/db (:conn db/datomic-cloud))
        user-id (db/find-user-by-user-name user)
        ongoing-prop (db/find-ongoing-proposition db)]
    (cond
      (nil? ongoing-prop)
      (no-content)

      (some? user-id)
      (ok (sort-by :bet/amount
                   #(compare %2 %1)
                   (db/find-prop-bets-for-user {:db          db
                                                :user-id     user-id
                                                :prop-bet-id ongoing-prop})))

      :else
      (not-found {:message "User not found."}))))

(defn verify-email
  [{:keys [body-params] :as req}]
  (let [{:keys [email token]} body-params
        {:keys [db/id user/verify-token user/status user/cash]} (db/pull-user
                                                                  {:user/email email
                                                                   :attrs [:db/id
                                                                           :user/cash
                                                                           {:user/status [:db/ident]}
                                                                           :user/verify-token]})]
    (cond
      (and (= token verify-token)
           (= :user.status/pending status))
      ;;TODO dont return 200 if db/verify-email fails
      (do
        (db/verify-email {:db/id id :user/cash cash})
        (ok {:message (format "Successfully verified %s" email)}))

      (= token verify-token)
      (ok {:message (format "Already verified %s" email)})

      :else
      (not-found {:message (format "Couldn't verify %s" email)}))))

(defn- existing-unused-recovery-token
  [{:keys [user/recovery]}]
  (some->> recovery
           (filter #(nil? (:recovery/used-time %)))
           first))

(defn account-recovery
  [{:keys [body-params] :as req}]
  (let [{:keys [user]} body-params
        db (d/db (:conn db/datomic-cloud))
        attrs [:db/id :user/email :user/first-name {:user/recovery
                                                    [:recovery/token
                                                     :recovery/issued-time
                                                     :recovery/used-time]}]
        user-entity (or (db/pull-user {:user/name user
                                       :db db
                                       :attrs     attrs})
                        (db/pull-user {:user/email user
                                       :db db
                                       :attrs attrs}))
        existing-unused-token (:recovery/token (existing-unused-recovery-token user-entity))
        token (or existing-unused-token (verify-email-token))]
    (if user-entity
      (do
        (when-not existing-unused-token
          (db/create-recovery-token {:user-id        (:db/id user-entity)
                                     :recovery/token token}))
        (ses/send-recovery-email (assoc user-entity :recovery/token token))
        (ok {:message "Check your email for instructions to reset your password."}))
      (not-found {:message "User not found."}))))

(defn account-recovery-set-new-password
  [{:keys [body-params] :as req}]
  (let [{:keys [email token new_password]} body-params
        invalid-input (when-not (re-matches valid-password new_password)
                        invalid-password)
        user-entity (db/pull-user {:user/email email
                                   :attrs      [:db/id {:user/recovery
                                                        [:db/id
                                                         :recovery/token
                                                         :recovery/issued-time
                                                         :recovery/used-time]}]})
        existing-token (existing-unused-recovery-token user-entity)]
    (cond
      (some? invalid-input)
      (conflict {:message invalid-input})

      (= token (:recovery/token existing-token))
      (do
        ;; TODO: combine to make this one transaction instead of two
        (db/update-password (:conn db/datomic-cloud)
                            {:db/id (:db/id user-entity)
                             :password (hash-password new_password)})
        (d/transact (:conn db/datomic-cloud) {:tx-data [{:db/id (:db/id existing-token)
                                                         :recovery/used-time (time/to-date)}]})
        (ok {:message "Successfully reset your password!"}))

      :else
      (not-found {:message "Couldn't reset your password."}))))
