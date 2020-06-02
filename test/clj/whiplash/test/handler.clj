(ns whiplash.test.handler
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :as mock]
    [clojure.string :as string]
    [whiplash.test.common :as common]
    [whiplash.db.core :as db]
    [datomic.client.api :as d]
    [clj-uuid :as uuid]
    [whiplash.time :as time]))

(common/app-fixtures)
(common/db-fixtures)

;; TODO: Setup running these tests automatically in AWS CodeBuild
(deftest test-app
  (testing "main route"
    (let [response ((common/test-app) (mock/request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "about"
    (let [response ((common/test-app) (mock/request :get "/about"))]
      (is (= 200 (:status response)))))

  (testing "control"
    (let [response ((common/test-app) (mock/request :get "/control"))]
      (is (= 200 (:status response)))))

  (testing "account"
    (let [response ((common/test-app) (mock/request :get "/account"))]
      (is (= 200 (:status response)))))

  (testing "recover"
    (let [response ((common/test-app) (mock/request :get "/user/password/recover"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((common/test-app) (mock/request :get "/invalid"))]
      (is (= 404 (:status response))))))

(deftest healthz
  (testing "healthz endpoint works"
    (let [response ((common/test-app) (mock/request :get "/v1/healthz"))]
      (is (= 200 (:status response))))))

(def dummy-user
  {:first_name "yas"
   :last_name "queen"
   :email "butt@cheek.com"
   :password "foobar2000"
   :user_name "queefburglar"})

(def dummy-user-2
  {:first_name "yas"
   :last_name "queen"
   :email "butt@crack.com"
   :password "foobar2000"
   :user_name "donniedarko"})

(def dummy-user-3
  {:first_name "Joan"
   :last_name "Walters"
   :email "butt@snack.com"
   :password "foobar2001"
   :user_name "kittycuddler420"})

(deftest test-user-400s
  (testing "cant get user, not logged in"
    (let [response ((common/test-app) (-> (mock/request :get "/user")))]
      (is (= 403 (:status response)))))

  (testing "post create user failure"
    (let [{:keys [status] :as response}
          ((common/test-app) (-> (mock/request :post "/user/create")
                                 (mock/json-body {:shit "yas"})))]
      (is (= 400 status))))

  (testing "can't login as nonexistent user"
    (let [login-resp ((common/test-app) (-> (mock/request :post "/user/login")
                                            (mock/json-body {:user_name (:user_name dummy-user)
                                                             :password (:password dummy-user)})))]

      (is (= 401 (:status login-resp))))))

(defn get-token-from-headers
  [headers]
  (some->> (get headers "Set-Cookie")
           (filter #(string/includes? % "value="))
           first
           (re-find #"^value=(.*)$")
           second))

(defn- verify-email
  [{:keys [email verify-token status]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/user/verify")
                                    (mock/json-body {:email email
                                                     :token verify-token})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- create-user
  ([]
   (create-user dummy-user))
  ([{:keys [first_name email admin? verify?] :as user}]
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
       (verify-email {:email email
                      :verify-token (:user/verify-token sent-email)}))

     (when admin?
       (d/transact (:conn db/datomic-cloud)
                   {:tx-data [{:db/id       (:db/id
                                              (db/pull-user {:user/email email
                                                             :attrs      [:db/id]}))
                               :user/status :user.status/admin}]}))

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

(deftest test-uncommon-name-success
  (create-user (assoc dummy-user :last_name "de Flandres")))

(deftest test-uncommon-name-success-2
  (create-user (assoc dummy-user :last_name "Sold-Toes")))

(deftest create-user-same-user-name-different-case-failure
  (create-user)
  (let [resp ((common/test-app) (-> (mock/request :post "/user/create")
                                    (mock/json-body (assoc dummy-user :email "butts@guts.com"
                                                                      :user_name "Queefburglar"))))
        parsed-body (common/parse-json-body resp)]

    (is (= 409 (:status resp)))
    (is (= {:message "User name taken"} parsed-body))))

(deftest create-user-same-email-different-case-failure
  (create-user)
  (let [resp ((common/test-app) (-> (mock/request :post "/user/create")
                                    (mock/json-body (assoc dummy-user :email "Butt@cheek.com"
                                                                      :user_name "queefburglar96"))))
        parsed-body (common/parse-json-body resp)]

    (is (= 409 (:status resp)))
    (is (= {:message "Email taken"} parsed-body))))

(defn- login
  ([]
   (login dummy-user))
  ([{:keys [user_name password] :as user}]
   (assert (and user_name password))
   (let [resp ((common/test-app) (-> (mock/request :post "/user/login")
                                     (mock/json-body {:user_name   user_name
                                                      :password password})))
         parsed-body (common/parse-json-body resp)
         auth-token (-> resp :headers get-token-from-headers)]

     (is (= 200 (:status resp)))
     (is (string? auth-token))

     {:auth-token auth-token
      :response (assoc resp :body parsed-body)})))

(defn- create-user-and-login
  ([]
   (create-user-and-login dummy-user))
  ([user]
   (let [create-user (create-user user)]
     (assoc (login user) :create-user create-user))))

(defn- change-password
  ([auth-token]
   (change-password auth-token dummy-user))
  ([auth-token {:keys [password] :as user}]
   (let [resp ((common/test-app) (-> (mock/request :post "/user/password")
                                     (mock/json-body {:password password})
                                     (mock/cookie :value auth-token)))
         parsed-body (common/parse-json-body resp)]

     (is (= 200 (:status resp)))

     (assoc resp :body parsed-body))))

(deftest create-user-and-change-password
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login)
        new-password "bigfarts"
        password-resp (change-password auth-token {:password new-password})
        logout-resp ((common/test-app) (-> (mock/request :post "/user/logout")))]
    (login {:user_name (:user_name dummy-user) :password new-password})))

(deftest create-user-and-change-password-failure
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login)]
    (is (= 409 (:status ((common/test-app) (-> (mock/request :post "/user/password")
                                               (mock/json-body {:password "bigfart"})
                                               (mock/cookie :value auth-token))))))))

(defn- get-user
  [{:keys [ga-tag? auth-token status]}]
  (let [ga-tag? (if (some? ga-tag?)
                  ga-tag?
                  true)
        resp (if ga-tag?
               ((common/test-app) (-> (mock/request :get "/user")
                                      (mock/cookie :_ga "GA1.2.1493569166.1576110731")
                                      (mock/cookie :value auth-token)))
               ((common/test-app) (-> (mock/request :get "/user")
                                      (mock/cookie :value auth-token))))
        parsed-body (common/parse-json-body resp)]
    (is (= (or status 200) (:status resp)))

    (assoc resp :body parsed-body)))

(deftest test-user
  (testing "create and get user success "
    (let [{:keys [email first_name last_name user_name]} dummy-user
          {:keys [auth-token] login-resp :response} (create-user-and-login (assoc dummy-user :verify? false))
          login-fail-resp ((common/test-app) (-> (mock/request :post "/user/login")
                                                 (mock/json-body {:user_name user_name
                                                                  :password  "wrong_password"})))
          get-success-resp (get-user {:auth-token auth-token})
          create-again-fail ((common/test-app) (-> (mock/request :post "/user/create")
                                                   (mock/json-body dummy-user)))]

      (is (= #:user{:email      email
                    :first-name first_name
                    :last-name  last_name
                    :status     "user.status/pending"
                    :name       user_name
                    :cash       500
                    :notifications []}
             (:body get-success-resp)))

      (is (= 401 (:status login-fail-resp)))
      (is (= 409 (:status create-again-fail))))))

(deftest logout
  (testing "not logged in"
    (let [resp ((common/test-app) (-> (mock/request :post "/user/logout")))]
      (is (= 403 (:status resp)))))

  (testing "logging out returns cookie set to deleted, can log in after logging out"
    (let [{:keys [auth-token] :as login-resp} (create-user-and-login)
          resp ((common/test-app) (-> (mock/request :post "/user/logout")
                                      (mock/cookie :value auth-token)))
          login-again-resp (login)]
      (is (string/includes? (get-token-from-headers (:headers resp)) "deleted"))
      (is (= 200 (:status resp))))))

(deftest login-with-email-address
  (create-user)
  (login {:user_name (:email dummy-user)
          :password (:password dummy-user)}))

(defn- create-user-failure
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

(deftest bad-create-user-inputs
  (is (= "First name invalid"
         (-> (create-user-failure (assoc dummy-user :first_name "1"))
             :body
             :message)))
  (is (= "Last name invalid"
         (-> (create-user-failure (assoc dummy-user :last_name "1"))
             :body
             :message)))
  (is (= "User name invalid"
         (-> (create-user-failure (assoc dummy-user :user_name ""))
             :body
             :message)))
  (is (= "User name invalid"
         (-> (create-user-failure (assoc dummy-user :user_name "donnie@ronnie.com"))
             :body
             :message)))
  (is (= "User name invalid"
         (-> (create-user-failure (assoc dummy-user :user_name "user-qwertyuiopasdfghjklzx"))
             :body
             :message)))
  (is (= "User name invalid"
         (-> (create-user-failure (assoc dummy-user :user_name "USER-qwerTyuiopaSdfghjklzx"))
             :body
             :message)))
  (is (= "Password must be at least 8 characters"
         (-> (create-user-failure (assoc dummy-user :password "1234567"))
             :body
             :message)))

  (is (= "Email invalid"
         (-> (create-user-failure (assoc dummy-user :email "1234567"))
             :body
             :message)))

  (is (= "Email invalid"
         (-> (create-user-failure (assoc dummy-user :email "1@2.c"))
             :body
             :message))))

(deftest verify-email-test
  (testing "verify get"
    (let [response ((common/test-app) (mock/request :get "/user/verify"))]
      (is (= 200 (:status response)))))

  (testing "verify email post"
    (let [{:keys [auth-token] :as login-resp} (create-user-and-login (assoc dummy-user :verify? false))
          verify-token (get-in login-resp [:create-user :token])
          {:keys [body] :as get-success-resp} (get-user {:auth-token auth-token})
          {:keys [user/email]} body
          verify-resp (verify-email {:email email
                                     :verify-token verify-token})
          try-verify-again-resp (verify-email {:email email
                                               :verify-token verify-token})
          failed-verify-resp (verify-email {:email email
                                            :verify-token "you only yolo once"
                                            :status 404})
          get-verified-user (get-user {:auth-token auth-token})]
      (is (= {:message (format "Successfully verified %s" email)}
             (:body verify-resp)))

      (is (= {:message (format "Already verified %s" email)}
             (:body try-verify-again-resp)))

      (is (= {:message (format "Couldn't verify %s" email)}
             (:body failed-verify-resp)))

      (is (= #:user{:cash          500
                    :email         "butt@cheek.com"
                    :first-name    "yas"
                    :last-name     "queen"
                    :name          "queefburglar"
                    :notifications []
                    :status        "user.status/active"}
             (:body get-verified-user))))))

(defn- all-time-top-ten
  [{:keys [status]}]
  (let [resp ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))]
    (is (= (:status resp)
           (or status 200)))
    (assoc resp :body (common/parse-json-body resp))))

(deftest all-time-top-ten-test
  (testing "only returns 10 users"
    (doseq [x (range 12)]
      (create-user (assoc dummy-user :email (str x "@poops.com") :user_name (str x))))
    (let [all-time-leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))]
      (is (= 10
             (count (common/parse-json-body all-time-leaderboard-resp)))))))

;; Prop betting MVP tests

(defn- admin-create-event
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

(defn- admin-end-event
  [{:keys [auth-token status]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/event/end")
                                    (mock/cookie :value auth-token)))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- admin-create-prop
  [{:keys [auth-token text end-betting-secs status]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/prop")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:text             text
                                                     :end-betting-secs (or end-betting-secs
                                                                           30)})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- admin-end-prop
  [{:keys [auth-token result]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/prop/end")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:result result})))]
    (is (= 200 (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- admin-flip-prop-outcome
  [{:keys [auth-token status]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/prop/flip-previous")
                                    (mock/cookie :value auth-token)))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- get-event
  ([]
   (get-event {}))
  ([{:keys [status]}]
   (let [resp ((common/test-app) (-> (mock/request :get "/stream/event")))]
     (is (= (or status
                200)
            (:status resp)))
     (assoc resp :body (common/parse-json-body resp)))))

(defn- get-event-leaderboard
  ([]
   (get-event-leaderboard {}))
  ([{:keys [status]}]
   (let [resp ((common/test-app) (-> (mock/request :get "/leaderboard/event")))]
     (is (= (or status
                200)
            (:status resp)))
     (assoc resp :body (common/parse-json-body resp)))))

(defn- get-prop
  ([]
   (get-prop {}))
  ([{:keys [status]}]
   (let [resp ((common/test-app) (-> (mock/request :get "/stream/prop")))]
     (is (= (or status
                200)
            (:status resp)))
     ;; Access-Control headers needed for CORS
     (is (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept"
             "Access-Control-Allow-Methods" "GET"
             "Access-Control-Allow-Origin"  "*"
             "Cache-Control"                "max-age=1"
             "Content-Type"                 "application/json; charset=utf-8"
             "X-Content-Type-Options"       "nosniff"
             "X-Frame-Options"              "SAMEORIGIN"
             "X-XSS-Protection"             "1; mode=block"}
            (:headers resp)))
     (assoc resp :body (common/parse-json-body resp)))))

(deftest options-proposition
  (testing "need this endpoint and headers for CORS (twitch extension)"
    (let [resp ((common/test-app) (-> (mock/request :options "/stream/prop")))]
      (is (= 204
             (:status resp)))
      (is (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept"
              "Access-Control-Allow-Methods" "GET"
              "Access-Control-Allow-Origin"  "*"
              "Content-Type"                 "application/octet-stream"
              "X-Content-Type-Options"       "nosniff"
              "X-Frame-Options"              "SAMEORIGIN"
              "X-XSS-Protection"             "1; mode=block"}
             (:headers resp))))))

(defn- user-place-prop-bet
  [{:keys [auth-token projected-result bet-amount status ga-tag?]}]
  (let [ga-tag? (if (some? ga-tag?)
                  ga-tag?
                  true)
        resp (if ga-tag?
               ((common/test-app) (-> (mock/request :post "/user/prop-bet")
                                      ;; All requests should have this cookie set by google analytics
                                      (mock/cookie :_ga "GA1.2.1493569166.1576110731")
                                      (mock/cookie :value auth-token)
                                      (mock/json-body {:projected_result projected-result
                                                       :bet_amount       bet-amount})))
               ((common/test-app) (-> (mock/request :post "/user/prop-bet")
                                      ;; All requests should have this cookie set by google analytics
                                      (mock/cookie :value auth-token)
                                      (mock/json-body {:projected_result projected-result
                                                       :bet_amount       bet-amount}))))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- user-get-prop-bets
  [{:keys [auth-token status]}]
  (let [resp ((common/test-app) (-> (mock/request :get "/user/prop-bet")
                                    (mock/cookie :value auth-token)))]
    (is (= (or status 200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- get-prop-bets-leaderboard
  []
  (let [resp ((common/test-app) (-> (mock/request :get "/leaderboard/prop-bets")))]
    (is (= 200 (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- user-submit-suggestion
  [{:keys [auth-token text status]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/user/suggestion")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:text text})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- admin-get-suggestions
  [{:keys [auth-token status]}]
  (let [resp ((common/test-app) (-> (mock/request :get "/admin/suggestion")
                                    (mock/cookie :value auth-token)))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- admin-dismiss-suggestions
  [{:keys [auth-token status suggestions]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/suggestion")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:suggestions suggestions})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- admin-create-next-event-ts
  [{:keys [auth-token status ts]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/event/countdown")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:ts ts})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(deftest fail-admin-create-event
  (testing "fail to create event because not admin"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)]
      (admin-create-event {:auth-token auth-token
                           :title "poops"
                           :channel-id "pig boops"
                           :status 403}))))

(deftest success-admin-create-event
  (testing "successfully create and get event with proper admin role"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          title "Dirty Dan's Delirious Dance Party"
          twitch-user "drdisrespect"
          resp (admin-create-event {:auth-token auth-token
                                    :title title
                                    :channel-id twitch-user})
          fail-create-again-resp (admin-create-event {:auth-token auth-token
                                                      :title title
                                                      :channel-id twitch-user
                                                      :status 405})

          get-event-response (get-event)
          get-response-body (:body get-event-response)

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text text})

          success-get-running-prop-resp  (get-prop)

          success-get-prop-body (:body success-get-running-prop-resp)
          current-prop (:current-prop success-get-prop-body)

          fail-end-event-resp (admin-end-event {:auth-token auth-token
                                                :status 405})

          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                             :result true})

          end-event-resp (admin-end-event {:auth-token auth-token})
          get-after-end-resp (get-event {:status 404})]

      (is (string? (:proposition/start-time current-prop)))
      (is (string? (:proposition/betting-end-time current-prop)))
      (is (= #:proposition{:running? true
                           :betting-seconds-left 29
                           :text text}
             (dissoc current-prop :proposition/start-time :proposition/betting-end-time)))

      (is (string? (:event/start-time get-response-body)))
      (is (= #:event{:running? true
                     :title title
                     :stream-source "event.stream-source/twitch"
                     :channel-id twitch-user}
             (dissoc get-response-body :event/start-time))))))

(deftest fail-create-empty-prop
  (testing "successfully create and get event with proper admin role"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          title "Dirty Dan's Delirious Dance Party"
          twitch-user "drdisrespect"
          resp (admin-create-event {:auth-token auth-token
                                    :title title
                                    :channel-id twitch-user})
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text ""
                                                   :status 405})])))

(deftest success-get-event
  (testing "successfully get nonexistent event, don't need admin"
    (get-event {:status 404})))

(deftest fail-end-event
  (testing "fails because user is not an admin"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)]
      (admin-end-event {:auth-token auth-token
                        :status 403}))))

(deftest no-event-to-end
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))]
    (admin-end-event {:auth-token auth-token
                      :status 405})))

(deftest admin-and-user-create-prop-bet
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        event-score-before-event-creation (get-event-leaderboard {:status 404})

        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id twitch-user})

        event-score-before-prop-creation (get-event-leaderboard {:status 404})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text})

        ;; have admin bet for notifications
        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result false
                                :bet-amount       500})

        _ (create-user dummy-user-2)
        _ (create-user dummy-user-3)

        ;; user 2 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-2)

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       100})

        user-place-prop-bet-resp-a (user-place-prop-bet {:auth-token       auth-token
                                                         :projected-result true
                                                         :bet-amount       100})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       300})

        user-get-prop-bet-resp (user-get-prop-bets {:auth-token auth-token})

        get-body (:body user-get-prop-bet-resp)

        ;; user 3 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-3)

        user-place-prop-bet-resp3 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       100})
        user-place-prop-bet-resp4 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       400})

        user-get-prop-bet-resp2 (user-get-prop-bets {:auth-token auth-token})
        get-body2 (:body user-get-prop-bet-resp2)

        current-prop-bets-response (get-prop-bets-leaderboard)

        event-score-before-prop-result (get-event-leaderboard)
        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     true})

        event-score-before-end (get-event-leaderboard)

        admin-get-user (get-user {:auth-token auth-token})
        admin-get-user-notifs-acked (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user (get-user {:auth-token auth-token})
        user2-get-user-notifs-acked (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-3)
        user3-get-user (get-user {:auth-token auth-token})
        user3-get-user-notifs-acked (get-user {:auth-token auth-token})

        ;;admin end event
        {:keys [auth-token] login-resp :response} (login)
        end-event-resp (admin-end-event {:auth-token auth-token})

        event-score-after-end (get-event-leaderboard)]

    (is (= [#:bet{:amount            300
                  :projected-result? false}
            #:bet{:amount            100
                  :projected-result? true}
            #:bet{:amount            100
                  :projected-result? true}]
           (mapv #(dissoc % :bet/time) get-body)))

    (is (= [#:bet{:amount            100
                  :projected-result? false}
            #:bet{:amount            400
                  :projected-result? true}]
           (->> get-body2
                (mapv #(dissoc % :bet/time))
                (sort-by :bet/amount))))

    (is (= {:false {:bets  [{:bet/amount 500
                             :user/name  "queefburglar"}
                            {:bet/amount 300
                             :user/name  "donniedarko"}
                            {:bet/amount 100
                             :user/name  "kittycuddler420"}]
                    :odds  1.666666666666667
                    :total 900}
            :true  {:bets  [{:bet/amount 400
                             :user/name  "kittycuddler420"}
                            {:bet/amount 200
                             :user/name  "donniedarko"}]
                    :odds  2.5
                    :total 600}}
           (:body current-prop-bets-response)))

    (is (= []
           (:body event-score-before-event-creation)
           (:body event-score-before-prop-creation)))

    (is (= [{:score     0
             :user_name "queefburglar"}
            {:score     0
             :user_name "donniedarko"}
            {:score     0
             :user_name "kittycuddler420"}]
           (:body event-score-before-prop-result)))

    (is (= [{:score     500
             :user_name "kittycuddler420"}
            {:score     0
             :user_name "donniedarko"}
            {:score     -500
             :user_name "queefburglar"}]
           (:body event-score-before-end)
           (:body event-score-after-end)))

    ;; notifications
    ;; admin lost it all and got bailed out
    (is (= 100 (-> admin-get-user :body :user/cash)))
    (is (= [#:notification{:type "notification.type/bailout"}]
           (-> admin-get-user :body :user/notifications)))
    (is (= []
           (-> admin-get-user-notifs-acked :body :user/notifications)))

    (is (= 500 (-> user2-get-user :body :user/cash)))
    ;; 2 payouts for the same proposition coalesced into one notification
    (is (= [{:bet/payout          500
             :notification/type   "notification.type/payout"
             :proposition/result? true
             :proposition/text    "Will Jon wipeout 2+ times this round?"}]
           (-> user2-get-user :body :user/notifications)))
    (is (= []
           (-> user2-get-user-notifs-acked :body :user/notifications)))

    (is (= 1000 (-> user3-get-user :body :user/cash)))
    (is (= [{:bet/payout          1000
             :notification/type   "notification.type/payout"
             :proposition/result? true
             :proposition/text    "Will Jon wipeout 2+ times this round?"}]
           (-> user3-get-user :body :user/notifications)))
    (is (= []
           (-> user3-get-user-notifs-acked :body :user/notifications)))))

;;TODO test for no previous outcome to flip
(deftest flip-proposition-outcome
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id twitch-user})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text})

        ;; have admin bet for notifications
        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result false
                                :bet-amount       400})

        _ (create-user dummy-user-2)
        _ (create-user dummy-user-3)

        ;; user 2 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-2)

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       100})

        user-place-prop-bet-respa (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       100})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       300})

        user-get-prop-bet-resp (user-get-prop-bets {:auth-token auth-token})

        ;; user 3 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-3)

        user-place-prop-bet-resp3 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       100})
        user-place-prop-bet-resp4 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       400})

        user-get-prop-bet-resp2 (user-get-prop-bets {:auth-token auth-token})

        current-prop-bets-response (get-prop-bets-leaderboard)

        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     true})

        event-score-before-flip (get-event-leaderboard)

        {:keys [auth-token] login-resp :response} (login)
        ;; flip proposition outcome
        flip-outcome-resp (admin-flip-prop-outcome {:auth-token auth-token})
        event-score-after-flip (get-event-leaderboard)

        admin-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-3)
        user3-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login)
        ;;admin end event
        end-event-resp (admin-end-event {:auth-token auth-token})]

    (is (= {:false {:bets  [{:bet/amount 400
                             :user/name  "queefburglar"}
                            {:bet/amount 300
                             :user/name  "donniedarko"}
                            {:bet/amount 100
                             :user/name  "kittycuddler420"}]
                    :odds  1.75
                    :total 800}
            :true  {:bets  [{:bet/amount 400
                             :user/name  "kittycuddler420"}
                            {:bet/amount 200
                             :user/name  "donniedarko"}]
                    :odds  2.333333333333333
                    :total 600}}
           (:body current-prop-bets-response)))

    (is (= [{:score     433
             :user_name "kittycuddler420"}
            {:score     -34
             :user_name "donniedarko"}
            {:score     -400
             :user_name "queefburglar"}]
           (:body event-score-before-flip)))

    (is (= [{:score     300
             :user_name "queefburglar"}
            {:score     25
             :user_name "donniedarko"}
            {:score     -325
             :user_name "kittycuddler420"}]
           (:body event-score-after-flip)))

    ;; notifications
    (is (= 800 (-> admin-get-user :body :user/cash)))
    (is (= [{:bet/payout          700
             :notification/type   "notification.type/payout"
             :proposition/result? false
             :proposition/text    "Will Jon wipeout 2+ times this round?"}]
           (-> admin-get-user :body :user/notifications)))

    (is (= 525 (-> user2-get-user :body :user/cash)))
    (is (= [{:bet/payout          525
             :notification/type   "notification.type/payout"
             :proposition/result? false
             :proposition/text    "Will Jon wipeout 2+ times this round?"}
            #:notification{:type "notification.type/bailout"}]
           (-> user2-get-user :body :user/notifications)))

    (is (= 175 (-> user3-get-user :body :user/cash)))
    (is (= [{:bet/payout          175
             :notification/type   "notification.type/payout"
             :proposition/result? false
             :proposition/text    "Will Jon wipeout 2+ times this round?"}
            #:notification{:type "notification.type/bailout"}]
           (-> user3-get-user :body :user/notifications)))))

(deftest flip-prop-outcome-test-bail
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id twitch-user})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text})

        ;; have admin bet for notifications
        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result true
                                :bet-amount       500})

        _ (create-user dummy-user-2)

        ;; user 2 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-2)

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result false
                                                       :bet-amount       200})

        user-get-prop-bet-resp (user-get-prop-bets {:auth-token auth-token})

        current-prop-bets-response (get-prop-bets-leaderboard)

        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     true})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user-before-flip (get-user {:auth-token auth-token})

        event-score-before-flip (get-event-leaderboard)

        {:keys [auth-token] login-resp :response} (login)
        admin-get-user-before-flip (get-user {:auth-token auth-token})
        ;; flip proposition outcome
        flip-outcome-resp (admin-flip-prop-outcome {:auth-token auth-token})
        event-score-after-flip (get-event-leaderboard)

        admin-get-user-after-flip (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user-after-flip (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login)
        ;;admin end event
        end-event-resp (admin-end-event {:auth-token auth-token})]

    (is (= {:false {:bets  [{:bet/amount 200
                             :user/name  "donniedarko"}]
                    :odds  3.5
                    :total 200}
            :true  {:bets  [{:bet/amount 500
                             :user/name  "queefburglar"}]
                    :odds  1.4
                    :total 500}}
           (:body current-prop-bets-response)))

    (is (= [{:score     200
             :user_name "queefburglar"}
            {:score     -200
             :user_name "donniedarko"}]
           (:body event-score-before-flip)))

    (is (= [{:score     500
             :user_name "donniedarko"}
            {:score     -500
             :user_name "queefburglar"}]
           (:body event-score-after-flip)))

    ;; notifications
    (is (= 700 (-> admin-get-user-before-flip :body :user/cash)))
    (is (= [{:bet/payout          700
             :notification/type   "notification.type/payout"
             :proposition/result? true
             :proposition/text    "Will Jon wipeout 2+ times this round?"}]
           (-> admin-get-user-before-flip :body :user/notifications)))

    (is (= 100 (-> admin-get-user-after-flip :body :user/cash)))
    (is (= [#:notification{:type "notification.type/bailout"}]
           (-> admin-get-user-after-flip :body :user/notifications)))

    (is (= 300 (-> user2-get-user-before-flip :body :user/cash)))
    (is (= []
           (-> user2-get-user-before-flip :body :user/notifications)))

    (is (= 1000 (-> user2-get-user-after-flip :body :user/cash)))
    (is (= [{:bet/payout          700
             :notification/type   "notification.type/payout"
             :proposition/result? false
             :proposition/text    "Will Jon wipeout 2+ times this round?"}]
           (-> user2-get-user-after-flip :body :user/notifications)))))

(deftest cant-bet-email-not-verified
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id twitch-user})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text})

        _ (create-user (assoc dummy-user-2 :verify? false))

        {:keys [auth-token] login-resp :response} (login dummy-user-2)

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       100
                                                       :status 409})]))

(deftest no-payout-doesnt-break
  (testing ""
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))

          title "Dirty Dan's Delirious Dance Party"
          twitch-user "drdisrespect"
          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title title
                                                 :channel-id twitch-user})

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text text})

          user-place-prop-bet-resp (user-place-prop-bet {:auth-token auth-token
                                                         :projected-result true
                                                         :bet-amount 500})

          user-get-prop-bet-resp (user-get-prop-bets {:auth-token auth-token})
          get-body (:body user-get-prop-bet-resp)

          current-prop-bets-response (get-prop-bets-leaderboard)

          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                             :result false})

          ;;admin bet and reset to 100
          _ (is (= 100 (-> (get-user {:auth-token auth-token}) :body :user/cash)))

          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          resp (admin-end-event {:auth-token auth-token})]

      (is (= [#:bet{:amount            500
                    :projected-result? true}]
             (mapv #(dissoc % :bet/time) get-body)))

      (is (= {:true  {:bets  [{:bet/amount 500
                               :user/name  "queefburglar"}]
                      :odds  1.00
                      :total 500}
              :false {:bets []
                      :odds 500.00
                      :total 0}}
             (:body current-prop-bets-response))))))

(deftest bet-both-sides-doesnt-break
  (testing ""
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))

          title "Dirty Dan's Delirious Dance Party"
          twitch-user "drdisrespect"
          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title title
                                                 :channel-id twitch-user})

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text text})

          user-place-prop-bet-resp (user-place-prop-bet {:auth-token auth-token
                                                         :projected-result true
                                                         :bet-amount 300})

          user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token auth-token
                                                          :projected-result false
                                                          :bet-amount 200})

          user-get-prop-bet-resp (user-get-prop-bets {:auth-token auth-token})
          get-body (:body user-get-prop-bet-resp)

          current-prop-bets-response (get-prop-bets-leaderboard)

          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                             :result false})

          _ (is (= 500 (-> (get-user {:auth-token auth-token}) :body :user/cash)))

          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          end-event-resp (admin-end-event {:auth-token auth-token})

          event-score-resp (get-event-leaderboard)]

      (is (= [#:bet{:amount            300
                    :projected-result? true}
              #:bet{:amount            200
                    :projected-result? false}]
             (mapv #(dissoc % :bet/time) get-body)))

      (is (= {:false {:bets  [{:bet/amount 200
                              :user/name  "queefburglar"}]
                     :odds  2.5
                     :total 200}
             :true  {:bets  [{:bet/amount 300
                              :user/name  "queefburglar"}]
                     :odds  1.666666666666667
                     :total 300}}
             (:body current-prop-bets-response)))

      (is (= [{:score     0
               :user_name "queefburglar"}]
             (:body event-score-resp))))))

(deftest no-auth-cant-submit-suggestion
  (testing "invalid auth"
    (user-submit-suggestion {:auth-token "foo"
                             :text       "foo"
                             :status     403})))

(deftest user-cant-submit-suggestion-event
  (testing "no ongoing event"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)]
      (user-submit-suggestion {:auth-token auth-token
                               :text "foo"
                               :status 405}))))

(deftest user-cant-submit-suggestion-text
  (testing "invalid text"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))]
      (admin-create-event {:auth-token auth-token
                           :title "hi"
                           :channel-id "donnie"})
      (user-submit-suggestion {:auth-token auth-token
                               :text ""
                               :status 405})
      (user-submit-suggestion {:auth-token auth-token
                               :text "this string is going to be very long and possibly even over 100 characters wowee zowie. I don't know."
                               :status 405}))))

(deftest user-cant-suggest-email-not-verified
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        _ (admin-create-event {:auth-token auth-token
                               :title      "hi"
                               :channel-id "donnie"})

        _ (create-user (assoc dummy-user-2 :verify? false))

        {:keys [auth-token] login-resp :response} (login dummy-user-2)]

    (user-submit-suggestion {:auth-token auth-token
                             :text       "Hello this is a valid suggestion."
                             :status     405})))

(deftest dismiss-suggestions-success
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        _ (admin-create-event {:auth-token  auth-token
                               :title       "hi"
                               :channel-id "donnie"})
        _ (user-submit-suggestion {:auth-token auth-token
                                   :text       "Captain Falcon gets 2 or more dunks this round."})
        _ (user-submit-suggestion {:auth-token auth-token
                                   :text       "Dirty Dan dunks Donnie 17 or more times this round."})
        dismiss-text "This suggestion is going to get dismissed by an admin."

        _ (dotimes [_ 2]
            (user-submit-suggestion {:auth-token auth-token
                                     :text       dismiss-text}))

        get-suggestions-resp (admin-get-suggestions {:auth-token auth-token})
        suggestions-to-dismiss (->> get-suggestions-resp
                                    :body
                                    (filter #(= (:suggestion/text %) dismiss-text))
                                    (mapv :suggestion/uuid))

        _ (admin-dismiss-suggestions {:auth-token auth-token
                                      :suggestions suggestions-to-dismiss})

        ;; These UUIDs don't correspond to any suggestions
        _ (admin-dismiss-suggestions {:auth-token  auth-token
                                      :suggestions [(str (uuid/v4))]
                                      :status      404})
        get-suggestions-after-dismiss-resp (admin-get-suggestions {:auth-token auth-token})]

    (is (= [{:suggestion/text "This suggestion is going to get dismissed by an admin."
             :user/name       "queefburglar"}
            {:suggestion/text "This suggestion is going to get dismissed by an admin."
             :user/name       "queefburglar"}
            {:suggestion/text "Dirty Dan dunks Donnie 17 or more times this round."
             :user/name       "queefburglar"}
            {:suggestion/text "Captain Falcon gets 2 or more dunks this round."
             :user/name       "queefburglar"}]
           (->> get-suggestions-resp
                :body
                (mapv #(dissoc % :suggestion/submission-time :suggestion/uuid)))))
    (is (true?
          (every? #(and (string? (:suggestion/submission-time %))
                        (string? (:suggestion/uuid %)))
                  (:body get-suggestions-resp))))

    (is (= [{:suggestion/text "Dirty Dan dunks Donnie 17 or more times this round."
             :user/name       "queefburglar"}
            {:suggestion/text "Captain Falcon gets 2 or more dunks this round."
             :user/name       "queefburglar"}]
           (->> get-suggestions-after-dismiss-resp
                :body
                (mapv #(dissoc % :suggestion/submission-time :suggestion/uuid)))))

    (is (true?
          (every? #(and (string? (:suggestion/submission-time %))
                        (string? (:suggestion/uuid %)))
                  (:body get-suggestions-after-dismiss-resp))))))

(deftest admin-get-empty-suggestion-ongoing-event
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        _ (admin-create-event {:auth-token  auth-token
                               :title       "hi"
                               :channel-id "donnie"})
        get-suggestions (admin-get-suggestions {:auth-token auth-token
                                                :status 404})]
    (is (= [] (:body get-suggestions)))))

(deftest admin-get-empty-suggestion-no-event
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        get-suggestions (admin-get-suggestions {:auth-token auth-token
                                                :status 404})]
    (is (= [] (:body get-suggestions)))))

(deftest end-betting-for-proposition
  (let [{:keys [auth-token]} (create-user-and-login
                               (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title title
                                               :channel-id twitch-user})
        text "Will Jon wipeout 2+ times this round?"
        end-betting-secs 30
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text text
                                                 :end-betting-secs end-betting-secs})
        now (time/now)]
    (user-place-prop-bet {:auth-token auth-token
                          :projected-result true
                          :bet-amount 300})
    (is (string? (-> (get-prop) :body :current-prop :proposition/betting-end-time)))
    ;; TODO: change tests so we can move time forward more sanely
    ;; User cannot place a bet after the betting window is over
    (with-redefs [whiplash.time/now (fn []
                                      (time/seconds-delta now end-betting-secs))]
      (user-place-prop-bet {:auth-token       auth-token
                            :projected-result true
                            :bet-amount       200
                            :status           405}))
    (admin-end-prop {:auth-token auth-token
                     :result true})
    (admin-end-event {:auth-token auth-token
                     :result true})))

(deftest get-previous-prop
  (let [{:keys [auth-token]} (create-user-and-login
                               (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title title
                                               :channel-id twitch-user})
        text "Will Jon wipeout 2+ times this round?"
        end-betting-secs 30
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text text
                                                 :end-betting-secs end-betting-secs})]

    ;; asserts about current prop is good and previous prop is empty
    (is (empty? (-> (get-prop) :body :previous-prop)))
    (is (= text
           (-> (get-prop) :body :current-prop :proposition/text)))

    (admin-end-prop {:auth-token auth-token
                     :result true})
    (admin-create-prop {:auth-token auth-token
                        :text "foo"
                        :end-betting-secs end-betting-secs})

    ;; asserts about current prop and previous prop
    (is (= text
           (-> (get-prop) :body :previous-prop :proposition/text)))
    (is (= true
           (-> (get-prop) :body :previous-prop :proposition/result?)))
    (is (= "foo"
           (-> (get-prop) :body :current-prop :proposition/text)))

    (admin-end-prop {:auth-token auth-token
                     :result false})

    ;;asserts about prev prop but no current prop
    (is (= "foo"
           (-> (get-prop) :body :previous-prop :proposition/text)))
    (is (= false
           (-> (get-prop) :body :previous-prop :proposition/result?)))
    (is (empty? (-> (get-prop) :body :current-prop)))

    (admin-end-event {:auth-token auth-token
                      :result true})))

(deftest create-youtube-live-event
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dirty Dancing Watch Party"
        youtube-channel-id "x135bZ6G"
        resp (admin-create-event {:auth-token auth-token
                                  :title      title
                                  :channel-id youtube-channel-id
                                  :source "youtube"})
        fail-create-again-resp (admin-create-event {:auth-token auth-token
                                                    :title      title
                                                    :channel-id youtube-channel-id
                                                    :source "youtube"
                                                    :status     405})

        get-event-response (get-event)
        get-response-body (:body get-event-response)

        end-event-resp (admin-end-event {:auth-token auth-token})
        get-after-end-resp (get-event {:status 404})]

    (is (string? (:event/start-time get-response-body)))
    (is (= #:event{:running?      true
                   :title         title
                   :stream-source "event.stream-source/youtube"
                   :channel-id    youtube-channel-id}
           (dissoc get-response-body :event/start-time)))))

(deftest bad-event-source
  (testing "can't create event because source is invalid"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          title "Dirty Dan's Delirious Dirty Dancing Watch Party"
          youtube-channel-id "x135bZ6G"
          resp (admin-create-event {:auth-token auth-token
                                    :title      title
                                    :channel-id youtube-channel-id
                                    :source "blarp"
                                    :status 400})])))

(deftest empty-create-event-params
  (testing "can't create event because source is invalid"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          title "Dirty Dan's Delirious Dirty Dancing Watch Party"
          youtube-channel-id "x135bZ6G"
          resp (admin-create-event {:auth-token auth-token
                                    :title      title
                                    :channel-id youtube-channel-id
                                    :source     ""
                                    :status     400})]
      (admin-create-event {:auth-token auth-token
                           :title      title
                           :channel-id ""
                           :source     "youtube"
                           :status     400})
      (admin-create-event {:auth-token auth-token
                           :title      ""
                           :channel-id youtube-channel-id
                           :source     "youtube"
                           :status     400}))))

(deftest create-cnn-unauth-event
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dirty Dancing Watch Party"
        cnn-channel-id "doesnt matter what this is"
        resp (admin-create-event {:auth-token auth-token
                                  :title      title
                                  :channel-id cnn-channel-id
                                  :source "cnn-unauth"})
        fail-create-again-resp (admin-create-event {:auth-token auth-token
                                                    :title      title
                                                    :channel-id cnn-channel-id
                                                    :source "cnn-unauth"
                                                    :status     405})

        get-event-response (get-event)
        get-response-body (:body get-event-response)

        end-event-resp (admin-end-event {:auth-token auth-token})
        get-after-end-resp (get-event {:status 404})]

    (is (string? (:event/start-time get-response-body)))
    (is (= #:event{:running?      true
                   :title         title
                   :stream-source "event.stream-source/cnn-unauth"
                   :channel-id    cnn-channel-id}
           (dissoc get-response-body :event/start-time)))))

(deftest prop-bets-leaderboard-empty
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title title
                                               :channel-id twitch-user})
        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text text})
        current-prop-bets-response  (get-prop-bets-leaderboard)]
   (is (= {} (:body current-prop-bets-response)))))

(deftest multiple-propositions-correct-score
  (testing ""
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))

          title "Dirty Dan's Delirious Dance Party"
          twitch-user "drdisrespect"
          ;; Create and end to test event score leaderboard at end
          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title title
                                                 :channel-id twitch-user})

          end-event-resp (admin-end-event {:auth-token auth-token})

          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title title
                                                 :channel-id twitch-user})

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text text})

          _ (create-user dummy-user-2)
          _ (create-user dummy-user-3)

          ;; user 2 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-2)

          user-place-prop-bet-resp (user-place-prop-bet {:auth-token auth-token
                                                         :projected-result true
                                                         :bet-amount 200})

          user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token auth-token
                                                          :projected-result false
                                                          :bet-amount 300})

          ;; user 3 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-3)

          user-place-prop-bet-resp3 (user-place-prop-bet {:auth-token auth-token
                                                          :projected-result false
                                                          :bet-amount 100})
          user-place-prop-bet-resp4 (user-place-prop-bet {:auth-token auth-token
                                                          :projected-result true
                                                          :bet-amount 400})

          current-prop-bets-response  (get-prop-bets-leaderboard)

          event-score-before-prop-result (get-event-leaderboard)
          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                             :result true})
          all-time-leaderboard-first-prop ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))

          event-score-first-prop (get-event-leaderboard)

          create-second-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                          :text "zoinks"})

          ;; user 2 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-2)

          user-place-second-prop-bet-resp (user-place-prop-bet {:auth-token auth-token
                                                                :projected-result true
                                                                :bet-amount 50})

          ;; user 3 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-3)

          user-place-second-prop-bet-resp2 (user-place-prop-bet {:auth-token auth-token
                                                                 :projected-result false
                                                                 :bet-amount 70})

          prop-bets-second-response  (get-prop-bets-leaderboard)

          ;; login as admin
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                             :result false})

          event-score-second-prop (get-event-leaderboard)
          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          end-event-resp (admin-end-event {:auth-token auth-token})

          all-time-leaderboard-end ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))]

      (is (= {:false {:bets  [{:bet/amount 300
                               :user/name  "donniedarko"}
                              {:bet/amount 100
                               :user/name  "kittycuddler420"}]
                      :odds  2.5
                      :total 400}
              :true  {:bets  [{:bet/amount 400
                               :user/name  "kittycuddler420"}
                              {:bet/amount 200
                               :user/name  "donniedarko"}]
                      :odds  1.666666666666667
                      :total 600}}
             (:body current-prop-bets-response)))

      (is (= [{:score     0
               :user_name "donniedarko"}
              {:score     0
               :user_name "kittycuddler420"}]
             (:body event-score-before-prop-result)))

      (is (= [{:score     166
               :user_name "kittycuddler420"}
              {:score     -167
               :user_name "donniedarko"}]
             (:body event-score-first-prop)))

      (is (= [{:cash      666
               :user_name "kittycuddler420"}
              {:cash      500
               :user_name "queefburglar"}
              {:cash      333
               :user_name "donniedarko"}]
             (common/parse-json-body all-time-leaderboard-first-prop)))

      (is (= {:false {:bets  [{:bet/amount 70
                               :user/name  "kittycuddler420"}]
                      :odds  1.714285714285714
                      :total 70}
              :true  {:bets  [{:bet/amount 50
                               :user/name  "donniedarko"}]
                      :odds  2.4
                      :total 50}}
             (:body prop-bets-second-response)))

      (is (= [{:score     215
               :user_name "kittycuddler420"}
              {:score     -217
               :user_name "donniedarko"}]
             (:body event-score-second-prop)))

      (is (= [{:cash      715
               :user_name "kittycuddler420"}
              {:cash      500
               :user_name "queefburglar"}
              {:cash      283
               :user_name "donniedarko"}]
             (common/parse-json-body all-time-leaderboard-end)))

      ;; Pulling from previous event since current event is over
      (is (= [{:score     215
               :user_name "kittycuddler420"}
              {:score     -217
               :user_name "donniedarko"}]
             (:body (get-event-leaderboard)))))))

(deftest end-proposition-no-bets
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title title
                                               :channel-id twitch-user})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text text})
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result false})
        end-event-resp (admin-end-event {:auth-token auth-token})]))

(deftest weird-notifications-behavior-test
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title title
                                               :channel-id twitch-user})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text text})

        bet1 (user-place-prop-bet {:auth-token auth-token
                              :projected-result true
                              :bet-amount 500})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result false})

        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text "second one"})

        bet2 (user-place-prop-bet {:auth-token auth-token
                              :projected-result true
                              :bet-amount 50})

        bet3 (user-place-prop-bet {:auth-token auth-token
                                   :projected-result true
                                   :bet-amount 50})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result true})

        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text "third one"})

        bet4 (user-place-prop-bet {:auth-token auth-token
                                   :projected-result true
                                   :bet-amount 30})

        bet5 (user-place-prop-bet {:auth-token auth-token
                                   :projected-result true
                                   :bet-amount 30})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result true})

        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text "fourth one"})

        bet6 (user-place-prop-bet {:auth-token auth-token
                                   :projected-result true
                                   :bet-amount 10})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result false})

        ;; normally the get would happen on a regular interval between these requests
        get-user-resp (get-user {:auth-token auth-token})]

    ;; only one bailout notification, winnings coalesced

    (is (= [#:notification{:type "notification.type/bailout"}
            {:bet/payout          60
             :notification/type   "notification.type/payout"
             :proposition/result? true
             :proposition/text    "third one"}
            {:bet/payout          100
             :notification/type   "notification.type/payout"
             :proposition/result? true
             :proposition/text    "second one"}]
           (->> (-> get-user-resp :body :user/notifications)
                (sort-by :bet/payout)
                vec)))))

(deftest cant-create-next-event-ts-invalid-ts
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))]
    (admin-create-next-event-ts {:auth-token auth-token
                                 :status 400
                                 :ts "foobar"})))

(deftest cant-create-next-event-ts-in-past
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))]
    (admin-create-next-event-ts {:auth-token auth-token
                                 :status 400
                                 :ts "2000-04-01T22:56:01Z"})))

(deftest get-event-doesn't-return-old-next-event-ts
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        ts "2100-04-01T22:56:01Z"
        now (time/now)]
    (admin-create-next-event-ts {:auth-token auth-token :ts ts})

    (is (= {:whiplash/next-event-time ts}
           (:body (get-event))))

    ;; TODO: change tests so we can move time forward more sanely
    (with-redefs [whiplash.time/now (fn []
                                      (time/days-delta now (* 365 100)))]
      (get-event {:status 404}))))

(deftest create-next-event-ts
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        ts "2100-04-01T22:56:01Z"
        ts-2 "2200-04-01T22:56:01Z"]
    (admin-create-next-event-ts {:auth-token auth-token :ts ts})

    (is (= {:whiplash/next-event-time ts}
           (:body (get-event))))

    ;;implicitly tests that we're overwriting the original attribute, and not making a new entity
    (admin-create-next-event-ts {:auth-token auth-token :ts ts-2})
    (is (= {:whiplash/next-event-time ts-2}
           (:body (get-event))))))

(deftest get-event-returns-event-over-ts
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        ts "2100-04-01T22:56:01Z"]
    (admin-create-next-event-ts {:auth-token auth-token :ts ts})

    (is (= {:whiplash/next-event-time ts}
           (:body (get-event))))

    (admin-create-event {:auth-token auth-token
                         :title "poops"
                         :channel-id "pig boops"})

    (is (not= {:whiplash/next-event-time ts}
              (:body (get-event))))))

(defn- request-password-recovery
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

(defn- submit-password-recovery
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

(deftest recover-account-with-username
  (let [create-resp (create-user dummy-user)
        {:keys [recovery/token] :as request-recovery-resp} (request-password-recovery {:email-count 2})
        new-password "big_ole_bears"
        submit-password-recovery-resp (submit-password-recovery {:token token
                                                                 :password new-password})
        login-resp (login {:user_name (:user_name dummy-user)
                           :password new-password})
        request-recovery-resp2 (request-password-recovery {:email-count 3})
        submit-password-recovery-resp (submit-password-recovery {:token (:recovery/token request-recovery-resp2)
                                                                 :password new-password})
        login-resp2 (login {:user_name (:user_name dummy-user)
                            :password new-password})]
    ;; new token is issued after the old one is used
    (is (not= token (:recovery/token request-recovery-resp2)))))

(deftest recover-account-with-email
  (let [create-resp (create-user dummy-user)
        {:keys [recovery/token] :as request-recovery-resp} (request-password-recovery {:email-count 2
                                                                                       :method :email})
        new-password "big_ole_bears"
        submit-password-recovery-resp (submit-password-recovery {:token token
                                                                 :password new-password})
        login-resp (login {:user_name (:email dummy-user)
                           :password new-password})]))

(deftest recover-account-request-twice
  (testing "reuse an unused token"
    (let [create-resp (create-user dummy-user)
          request-recovery-resp (request-password-recovery {:email-count 2})
          {:keys [recovery/token]} (request-password-recovery {:email-count 3})
          new-password "big_ole_bears"
          submit-password-recovery-resp (submit-password-recovery {:token    token
                                                                   :password new-password})
          login-resp (login {:user_name (:user_name dummy-user)
                             :password  new-password})]
      (is (= token (:recovery/token request-recovery-resp))))))

(deftest recover-account-user-dne
  (let [create-resp (create-user dummy-user)
        {:keys [recovery/token] :as request-recovery-resp} (request-password-recovery
                                                             {:user        {:email "newemailwhodis"}
                                                              :method      :email
                                                              :email-count 0
                                                              :status      404})]))

(deftest recover-account-bad-token
  (let [create-resp (create-user dummy-user)
        {:keys [recovery/token] :as request-recovery-resp} (request-password-recovery {:email-count 2})
        new-password "big_ole_bears"
        submit-password-recovery-resp (submit-password-recovery {:token    "buh buh buh bad token"
                                                                 :password new-password
                                                                 :status   404})]
    (is (= 401
           (:status
             ;; TODO: refactor login so we can pass status
             ((common/test-app) (-> (mock/request :post "/user/login")
                                    (mock/json-body {:user_name (:user_name dummy-user)
                                                     :password  new-password}))))))))

(deftest recover-account-bad-email
  (let [create-resp (create-user dummy-user)
        {:keys [recovery/token] :as request-recovery-resp} (request-password-recovery {:email-count 2})
        new-password "big_ole_bears"
        submit-password-recovery-resp (submit-password-recovery {:email "does-not-exist"
                                                                 :token token
                                                                 :password new-password
                                                                 :status 404})]
    (is (= 401
           (:status
             ;; TODO: refactor login so we can pass status
             ((common/test-app) (-> (mock/request :post "/user/login")
                                    (mock/json-body {:user_name (:user_name dummy-user)
                                                     :password  new-password}))))))))

(deftest recover-account-bad-new-password
  (let [create-resp (create-user dummy-user)
        {:keys [recovery/token] :as request-recovery-resp} (request-password-recovery {:email-count 2})
        new-password "invalid"
        submit-password-recovery-resp (submit-password-recovery {:token token
                                                                 :status 409
                                                                 :password new-password})]
    (is (= 401
           (:status
             ;; TODO: refactor login so we can pass status
             ((common/test-app) (-> (mock/request :post "/user/login")
                                    (mock/json-body {:user_name (:user_name dummy-user)
                                                     :password  new-password}))))))))

(deftest valid-auth-or-ga-cookie?-middleware-test
  (user-place-prop-bet {:auth-token       nil
                       :projected-result false
                       :ga-tag?          false
                       :bet-amount       500
                       :status           403})
  (get-user {:auth-token nil
             :ga-tag? false
             :status 403}))

(deftest not-logged-in-user-can-bet
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        event-score-before-event-creation (get-event-leaderboard {:status 404})

        title "Dirty Dan's Delirious Dance Party"
        twitch-user "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id twitch-user})

        event-score-before-prop-creation (get-event-leaderboard {:status 404})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text})

        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result false
                                :bet-amount       500})

        ;; logged out user bets
        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       nil
                                                       :projected-result true
                                                       :bet-amount       100})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       nil
                                                       :projected-result true
                                                       :bet-amount       400})

        user-get-prop-bet-resp (user-get-prop-bets {:auth-token nil
                                                    :status 403})
        get-body (:body user-get-prop-bet-resp)

        current-prop-bets-response (get-prop-bets-leaderboard)
        event-score-before-prop-result (get-event-leaderboard)

        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     false})

        event-score-before-end (get-event-leaderboard)

        admin-get-user (get-user {:auth-token auth-token})
        admin-get-user-notifs-acked (get-user {:auth-token auth-token})

        user2-get-user (get-user nil)
        user2-get-user-notifs-acked (get-user nil)

        ;;admin end event
        {:keys [auth-token] login-resp :response} (login)
        end-event-resp (admin-end-event {:auth-token auth-token})

        event-score-after-end (get-event-leaderboard)
        all-time-leaderboard (all-time-top-ten {})]

    ;; need auth to hit this endpoint
    (is (= get-body {:message "Access to /user/prop-bet is not authorized"}))

    (is (= {:false {:bets  [{:bet/amount 500
                             :user/name  "queefburglar"}]
                    :odds  2.0
                    :total 500}
            :true  {:bets  [{:bet/amount 500
                             :user/name  "user-cAcwygPxycxxcPNxcczNgc"}]
                    :odds  2.0
                    :total 500}}
           (:body current-prop-bets-response)))

    (is (= []
           (:body event-score-before-event-creation)
           (:body event-score-before-prop-creation)))

    (testing "unauth user does not appear on current event leaderboard"
      (is (= [{:score     0
               :user_name "queefburglar"}]
             (:body event-score-before-prop-result))))

    (is (= [{:score     500
             :user_name "queefburglar"}]
           (:body event-score-before-end)
           (:body event-score-after-end)))

    (testing "unauth user does not appear in all time leaderboard"
      (is (= [{:cash      1000
               :user_name "queefburglar"}]
             (:body all-time-leaderboard))))

    ;; notifications
    (is (= 1000 (-> admin-get-user :body :user/cash)))
    (is (= [{:bet/payout          1000
             :notification/type   "notification.type/payout"
             :proposition/result? false
             :proposition/text    "Will Jon wipeout 2+ times this round?"}]
           (-> admin-get-user :body :user/notifications)))
    (is (= []
           (-> admin-get-user-notifs-acked :body :user/notifications)))

    (is (= 0 (-> user2-get-user :body :user/cash)))
    (is (= [#:notification{:type "notification.type/no-bailout"}]
           (-> user2-get-user :body :user/notifications)))
    (is (= []
           (-> user2-get-user-notifs-acked :body :user/notifications)))))
