(ns whiplash.test.helpers
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [whiplash.test.common :as common]
            [ring.mock.request :as mock]
            [datomic.client.api :as d]
            [whiplash.db.core :as db]))

(def dummy-user
  {:first_name "yas"
   :last_name  "queen"
   :email      "butt@cheek.com"
   :password   "foobar2000"
   :user_name  "queefburglar"})

(def dummy-user-2
  {:first_name "yas"
   :last_name  "queen"
   :email      "butt@crack.com"
   :password   "foobar2000"
   :user_name  "donniedarko"})

(def dummy-user-3
  {:first_name "Joan"
   :last_name  "Walters"
   :email      "butt@snack.com"
   :password   "foobar2001"
   :user_name  "kittycuddler420"})

(defn get-token-from-headers
  [headers]
  (some->> (get headers "Set-Cookie")
           (filter #(string/includes? % "value="))
           first
           (re-find #"^value=(.*)$")
           second))

(defn verify-email
  [{:keys [email verify-token status]}]
  (assert (and email verify-token))
  (let [resp ((common/test-app) (-> (mock/request :post "/user/verify")
                                    (mock/json-body {:email email
                                                     :token verify-token})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn create-user
  ([]
   (create-user dummy-user))
  ([{:keys [first_name email admin? verify? mod?] :as user}]
   (assert user)
   (let [verify? (if (nil? verify?)
                   true
                   verify?)
         resp ((common/test-app) (-> (mock/request :post "/user/create")
                                     (mock/json-body user)))
         parsed-body (common/parse-json-body resp)
         sent-emails (-> common/test-state deref :emails)
         {:keys [body subject] :as sent-email} (first sent-emails)]

     (is (= 200 (:status resp)))
     (is (empty? parsed-body))

     (when verify?
       (verify-email {:email        email
                      :verify-token (:user/verify-token sent-email)}))

     (when admin?
       (d/transact (:conn db/datomic-cloud)
                   {:tx-data [{:db/id       (:db/id
                                              (db/pull-user {:user/email email
                                                             :attrs      [:db/id]}))
                               :user/status :user.status/admin}]}))
     (when mod?
       (d/transact (:conn db/datomic-cloud)
                   {:tx-data [{:db/id       (:db/id
                                              (db/pull-user {:user/email email
                                                             :attrs      [:db/id]}))
                               :user/status :user.status/mod}]}))

     (is (= 1 (count (filter #(= email (:user/email %))
                             sent-emails))))
     (is (= {:subject         "Whiplash: Please verify your email!"
             :user/email      email
             :user/first-name first_name
             :email/type      :email.type/verification}
            (dissoc sent-email :body :user/verify-token)))
     (is (some? (re-find #"https:\/\/www\.whiplashesports\.com\/user\/verify\?email=.*&token=.{32}" body)))
     (is (not (string/blank? (:user/verify-token sent-email))))

     (assoc resp :body parsed-body
                 :token (:user/verify-token sent-email)))))

(defn login
  ([]
   (login dummy-user))
  ([{:keys [user_name password] :as user}]
   (assert (and user_name password))
   (let [resp ((common/test-app) (-> (mock/request :post "/user/login")
                                     (mock/json-body {:user_name user_name
                                                      :password  password})))
         parsed-body (common/parse-json-body resp)
         auth-token (-> resp :headers get-token-from-headers)]

     (is (= 200 (:status resp)))
     (is (string? auth-token))

     {:auth-token auth-token
      :response   (assoc resp :body parsed-body)})))

(defn create-user-and-login
  ([]
   (create-user-and-login dummy-user))
  ([user]
   (let [create-user (create-user user)]
     (assoc (login user) :create-user create-user))))

(defn change-password
  ([auth-token]
   (change-password auth-token dummy-user))
  ([auth-token {:keys [password] :as user}]
   (let [resp ((common/test-app) (-> (mock/request :post "/user/password")
                                     (mock/json-body {:password password})
                                     (mock/cookie :value auth-token)))
         parsed-body (common/parse-json-body resp)]

     (is (= 200 (:status resp)))

     (assoc resp :body parsed-body))))

(defn get-user
  [{:keys [twitch-id? auth-token status]}]
  (let [#_#_ga-tag? (if (some? ga-tag?)
                      ga-tag?
                      true)
        twitch-id? (if (some? twitch-id?)
                     twitch-id?
                     true)
        resp (cond
               #_ga-tag?
               #_((common/test-app) (-> (mock/request :get "/user")
                                        (mock/cookie :_ga "GA1.2.1493569166.1576110731")
                                        (mock/cookie :value auth-token)))

               twitch-id?
               ((common/test-app) (-> (mock/request :get "/user")
                                      (mock/cookie :value auth-token)
                                      (mock/header "x-twitch-opaque-id" "UtestID123")))
               :else
               ((common/test-app) (-> (mock/request :get "/user")
                                      (mock/cookie :value auth-token))))
        parsed-body (common/parse-json-body resp)]
    (is (= (or status 200) (:status resp)))
    (when-not (= 403 (:status resp))
      (is (= {"Access-Control-Allow-Headers"     "Origin, Content-Type, Accept, X-Twitch-Opaque-ID"
              "Access-Control-Allow-Methods"     "GET"
              "Access-Control-Allow-Origin"      "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
              "Content-Type"                     "application/json; charset=utf-8"
              "X-Content-Type-Options"           "nosniff"
              "X-Frame-Options"                  "SAMEORIGIN"
              "X-XSS-Protection"                 "1; mode=block"}
             (:headers resp))))

    (assoc resp :body parsed-body)))

(defn create-user-failure
  ([]
   (create-user dummy-user))
  ([user]
   (assert user)
   (let [resp ((common/test-app) (-> (mock/request :post "/user/create")
                                     (mock/json-body user)))
         parsed-body (common/parse-json-body resp)]

     (is (= 409 (:status resp)))
     (is (some? parsed-body))

     (assoc resp :body parsed-body))))

(defn all-time-top-ten
  [{:keys [status]}]
  (let [resp ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))]
    (is (= (:status resp)
           (or status 200)))
    (assoc resp :body (common/parse-json-body resp))))

(defn admin-create-event
  [{:keys [auth-token title channel-id source status]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/event")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:title      title
                                                     :channel-id channel-id
                                                     :source     (or source
                                                                     "twitch")})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn admin-end-event
  [{:keys [auth-token status channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :post (format "/admin/event/end/%s" channel-id))
                                    (mock/cookie :value auth-token)))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn admin-create-prop
  [{:keys [auth-token text end-betting-secs channel-id status]}]
  (let [resp ((common/test-app) (-> (mock/request :post (format "/admin/prop/%s" channel-id))
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:text             text
                                                     :end-betting-secs (or end-betting-secs
                                                                           30)})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn admin-end-prop
  [{:keys [auth-token result channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :post (format "/admin/prop/end/%s" channel-id))
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:result result})))]
    (is (= 200 (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn admin-flip-prop-outcome
  [{:keys [auth-token status channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :post (format "/admin/prop/flip-previous/%s" channel-id))
                                    (mock/cookie :value auth-token)))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn get-events
  ([]
   (get-events {}))
  ([{:keys [status]}]
   (let [resp ((common/test-app) (-> (mock/request :get "/stream/events")))]
     (is (= (or status
                200)
            (:status resp)))
     (assoc resp :body (common/parse-json-body resp)))))

(defn get-event
  [{:keys [status channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :get (str "/stream/events/" channel-id))))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn get-prop
  [{:keys [status channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :get (format "/stream/prop/%s" channel-id))))]
    (is (= (or status
               200)
           (:status resp)))
    (is (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID"
            "Access-Control-Allow-Methods" "GET"
            "Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
            "Cache-Control"                "max-age=1"
            "Content-Type"                 "application/json; charset=utf-8"
            "X-Content-Type-Options"       "nosniff"
            "X-Frame-Options"              "SAMEORIGIN"
            "X-XSS-Protection"             "1; mode=block"}
           (:headers resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn get-event-leaderboard
  [{:keys [status channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :get (format "/leaderboard/event/%s" channel-id))))]
    (is (= (or status
               200)
           (:status resp)))
    (if (= (:status resp) 200)
      (is
        (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID"
            "Access-Control-Allow-Methods" "GET"
            "Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
            "Content-Type"                 "application/json; charset=utf-8"
            "X-Content-Type-Options"       "nosniff"
            "X-Frame-Options"              "SAMEORIGIN"
            "X-XSS-Protection"             "1; mode=block"}
           (:headers resp)))
      (is
        (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID"
            "Access-Control-Allow-Methods" "GET"
            "Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
            "Content-Type"                 "application/octet-stream"
            "X-Content-Type-Options"       "nosniff"
            "X-Frame-Options"              "SAMEORIGIN"
            "X-XSS-Protection"             "1; mode=block"}
           (:headers resp))))
    (if (= 200 (:status resp))
      (assoc resp :body (common/parse-json-body resp))
      resp)))

(defn user-place-prop-bet
  [{:keys [auth-token projected-result bet-amount status channel-id twitch-id?]}]
  (let [#_#_ga-tag? (if (some? ga-tag?)
                      ga-tag?
                      true)
        twitch-id? (if (some? twitch-id?)
                     twitch-id?
                     true)
        resp (cond
               #_ga-tag?                                      ;;unauth user
               #_((common/test-app) (-> (mock/request :post "/user/prop-bet")
                                        ;; All requests should have this cookie set by google analytics
                                        (mock/cookie :_ga "GA1.2.1493569166.1576110731")
                                        (mock/cookie :value auth-token)
                                        (mock/json-body {:projected_result projected-result
                                                         :bet_amount       bet-amount})))

               twitch-id?                                   ;;unauth twitch user
               ((common/test-app) (-> (mock/request :post (format "/user/prop-bet/%s" channel-id))
                                      (mock/header "x-twitch-opaque-id" "UtestID123")
                                      (mock/cookie :value auth-token)
                                      (mock/json-body {:projected_result projected-result
                                                       :bet_amount       bet-amount})))

               :else
               ((common/test-app) (-> (mock/request :post (format "/user/prop-bet/%s" channel-id))
                                      (mock/cookie :value auth-token)
                                      (mock/json-body {:projected_result projected-result
                                                       :bet_amount       bet-amount}))))]
    (is (= (or status
               200)
           (:status resp)))
    (when-not (= 403 (:status resp))
      (is (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID"
              "Access-Control-Allow-Methods" "POST, GET"
              "Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
              "Content-Type"                 "application/json; charset=utf-8"
              "X-Content-Type-Options"       "nosniff"
              "X-Frame-Options"              "SAMEORIGIN"
              "X-XSS-Protection"             "1; mode=block"}
             (:headers resp))))
    (assoc resp :body (common/parse-json-body resp))))

(defn get-prop-bets-leaderboard
  [{:keys [channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :get (format "/leaderboard/prop-bets/%s" channel-id))))]
    (is (= 200 (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn user-submit-suggestion
  [{:keys [auth-token text status channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :post (format "/user/suggestion/%s" channel-id))
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:text text})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn admin-get-suggestions
  [{:keys [auth-token status channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :get (format "/admin/suggestion/%s" channel-id))
                                    (mock/cookie :value auth-token)))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn admin-dismiss-suggestions
  [{:keys [auth-token status suggestions channel-id]}]
  (let [resp ((common/test-app) (-> (mock/request :post (format "/admin/suggestion/%s" channel-id))
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:suggestions suggestions})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

#_(defn admin-create-next-event-ts
  [{:keys [auth-token status ts]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/event/countdown")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:ts ts})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))


(defn request-password-recovery
  [{:keys [status user email-count method]}]
  (let [user (or user
                 dummy-user)
        method (or method :user_name)
        _ (assert (or (= :user_name method)
                      (= :email method)))
        resp ((common/test-app) (-> (mock/request :post "/user/password/request-recovery")
                                    (mock/json-body {:user (method user)})))
        sent-emails (-> common/test-state deref :emails)
        {:keys [body] :as sent-email} (first (filter #(= :email.type/recovery (:email/type %)) sent-emails))]

    (is (= (or status
               200)
           (:status resp)))

    (is (= (or email-count
               1)
           (count (filter #(= (:email user) (:user/email %)) sent-emails))))

    (when (> email-count 0)
      (is (= {:user/email      (:email user)
              :user/first-name (:first_name user)
              :email/type      :email.type/recovery
              :subject         "Whiplash: Reset your password"}
             (dissoc sent-email :body :recovery/token :db/id :user/recovery)))

      (is (some? (re-find #"https:\/\/www\.whiplashesports\.com\/user\/password\/recover\?email=.*&token=.{32}" body)))
      (is (not (string/blank? (:recovery/token sent-email)))))

    (assoc resp :body (common/parse-json-body resp)
                :recovery/token (:recovery/token sent-email))))

(defn submit-password-recovery
  [{:keys [status email token password]}]
  (let [email (or email (:email dummy-user))
        resp ((common/test-app) (-> (mock/request :post "/user/password/recover")
                                    (mock/json-body {:email        email
                                                     :token        token
                                                     :new_password password})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn twitch-ext-user-bet-on-10-props
  [auth-token channel-id]
  (dotimes [_ 10]
    (admin-create-prop {:auth-token auth-token
                        :text       "this is a propositon"
                        :channel-id channel-id})
    (user-place-prop-bet {:auth-token       nil
                          ;:ga-tag?          true
                          :twitch-id? true
                          :projected-result true
                          :bet-amount       100
                          :channel-id channel-id})

    (user-place-prop-bet {:auth-token       nil
                          ;:ga-tag?          true
                          :twitch-id? true
                          :projected-result false
                          :bet-amount       100
                          :channel-id channel-id})
    (admin-end-prop {:auth-token auth-token
                     :result     "false"
                     :channel-id channel-id})))
