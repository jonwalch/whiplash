(ns whiplash.test.handler
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :as mock]
    [clojure.string :as string]
    [whiplash.test.common :as common]
    [whiplash.guess-processor :as guess-processor]
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

  (testing "not-found route"
    (let [response ((common/test-app) (mock/request :get "/invalid"))]
      (is (= 404 (:status response))))))

(deftest static-content
  (testing "can get static content"
    (let [{:keys [status] :as response}
          ((common/test-app) (mock/request :get "/js/index.tsx"))]
      (is (= 200 status)))))

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

(defn- create-user
  ([]
   (create-user dummy-user))
  ([{:keys [first_name email admin?] :as user}]
   (assert user)
   (with-redefs [whiplash.integrations.amazon-ses/internal-send-verification-email
                 common/internal-send-verification-email-fake]
     (let [resp ((common/test-app) (-> (mock/request :post "/user/create")
                                       (mock/json-body user)))
           parsed-body (common/parse-json-body resp)
           sent-emails (-> common/test-state deref :emails)
           {:keys [body subject] :as sent-email} (first sent-emails)]

       (is (= 200 (:status resp)))
       (is (empty? parsed-body))

       (when admin?
         (d/transact (:conn db/datomic-cloud)
                     {:tx-data [{:db/id (:db/id
                                          (db/pull-user {:user/email email
                                                         :attrs      [:db/id]}))
                                 :user/status :user.status/admin}]}))

       (is (= 1 (count (filter #(= email (:user/email %))
                               sent-emails))))
       (is (= #:user{:first-name first_name
                     :email email}
              (select-keys sent-email [:user/first-name :user/email])))
       (is (= "Whiplash: Please verify your email!" subject))
       (is (some? (re-find #"https:\/\/www\.whiplashesports\.com\/user\/verify\?email=.*&token=.{32}" body)))
       (is (not (string/blank? (:user/verify-token sent-email))))

       (assoc resp :body parsed-body)))))

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
   (create-user user)
   (login user)))

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
  ([auth-token]
   (get-user auth-token dummy-user))
  ([auth-token {:keys [email first_name last_name user_name] :as user}]
   (let [resp ((common/test-app) (-> (mock/request :get "/user")
                                     (mock/cookie :value auth-token)))
         parsed-body (common/parse-json-body resp)]

     (is (= 200 (:status resp)))

     (assoc resp :body parsed-body))))

(deftest test-user
  (testing "create and get user success "
    (let [{:keys [email first_name last_name user_name]} dummy-user
          {:keys [auth-token] login-resp :response} (create-user-and-login)
          login-fail-resp ((common/test-app) (-> (mock/request :post "/user/login")
                                                 (mock/json-body {:user_name user_name
                                                                  :password  "wrong_password"})))
          get-success-resp (get-user auth-token)
          create-again-fail ((common/test-app) (-> (mock/request :post "/user/create")
                                                   (mock/json-body dummy-user)))]

      (is (not (string/blank? (-> get-success-resp :body :user/verify-token))))
      (is (= #:user{:email      email
                    :first-name first_name
                    :last-name  last_name
                    :status     "user.status/pending"
                    :name       user_name
                    :cash       500}
             (select-keys (:body get-success-resp)
                          [:user/email :user/first-name :user/last-name :user/status :user/name :user/cash])))

      (is (= 401 (:status login-fail-resp)))
      (is (= 409 (:status create-again-fail))))))

#_(def dummy-guess
  {;;:game-type "csgo"
   :match_name "Grand Final: Liquid vs ATK"
   :game_id   9388
   :match_id 549829
   :team_name "Liquid"
   :team_id   3213
   :bet_amount 75})

#_(def dummy-guess-2
  {;;:game-type "csgo"
   :match_name "Grand Final: Liquid vs ATK"
   :game_id   9389
   :match_id 549829
   :team_name "Liquid"
   :team_id   3213
   :bet_amount 25})

#_(defn- create-bet
  [auth-token guess]
  (let [resp ((common/test-app) (-> (mock/request :post "/user/guess")
                                    (mock/json-body guess)
                                    (mock/cookie :value auth-token)))
        parsed-body (common/parse-json-body resp)]
    (is (= 200 (:status resp)))

    (assoc resp :body parsed-body)))

#_(defn- get-bets
  [auth-token game-id match-id]
  (let [resp ((common/test-app) (-> (mock/request :get "/user/guess")
                                    (mock/query-string {:match_id match-id
                                                        :game_id  game-id})
                                    (mock/cookie :value auth-token)))
        parsed-body (common/parse-json-body resp)]
    (is (= 200 (:status resp)))

    (assoc resp :body parsed-body)))

#_(deftest add-guesses
  (testing "We can add and get guesses for a user"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)
          {:keys [game_id match_id]} dummy-guess
          create-guess-resp (create-bet auth-token dummy-guess)
          create-guess-resp2 (create-bet auth-token dummy-guess-2)
          create-guess-resp3 (create-bet auth-token dummy-guess-2)
          {:keys [body] :as get-guess-resp} (get-bets auth-token game_id match_id)
          get-guess-resp2 (get-bets auth-token
                                    (:game_id dummy-guess-2)
                                    (:match_id dummy-guess-2))
          bets-resp ((common/test-app) (-> (mock/request :get "/leaderboard/bets")
                                           (mock/query-string {:match_id (:match_id dummy-guess-2)
                                                               :game_id  (:game_id dummy-guess-2)})))]
      (is (= [{:bet/processed? false
               :bet/amount     (:bet_amount dummy-guess)
               :match/id (:match_id dummy-guess)
               :game/id   (:game_id dummy-guess)
               :team/id   (:team_id dummy-guess)
               :match/name (:match_name dummy-guess)
               :team/name      (:team_name dummy-guess)}]
             (mapv #(dissoc % :bet/time) body)))

      (is (= [{:bet/processed? false
               :bet/amount     (:bet_amount dummy-guess-2)
               :match/id (:match_id dummy-guess-2)
               :game/id   (:game_id dummy-guess-2)
               :team/id   (:team_id dummy-guess-2)
               :match/name (:match_name dummy-guess-2)
               :team/name      (:team_name dummy-guess-2)}
              {:bet/processed? false
               :bet/amount     (:bet_amount dummy-guess-2)
               :match/id (:match_id dummy-guess-2)
               :game/id   (:game_id dummy-guess-2)
               :team/id   (:team_id dummy-guess-2)
               :match/name (:match_name dummy-guess-2)
               :team/name      (:team_name dummy-guess-2)}]
             (mapv #(dissoc % :bet/time) (:body get-guess-resp2))))

      (is (= 375 (-> (get-user auth-token) :body :user/cash)))

      (is (= {:Liquid {:bets  [{:bet/amount 50
                                :user/name  "queefburglar"}]
                       :odds  1.0
                       :total 50}}
             (common/parse-json-body bets-resp)))

      (testing "Guess success processing works"
        (with-redefs [whiplash.integrations.pandascore/get-matches-request common/pandascore-finished-fake]
          (let [_ (guess-processor/process-bets)
                {:keys [body] :as get-guess-resp} (get-bets auth-token game_id match_id)
                get-guess-resp2 (get-bets auth-token
                                          (:game_id dummy-guess-2)
                                          (:match_id dummy-guess-2))
                #_#_leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/weekly")))
                all-time-leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))]

            (is (= [{:bet/payout     0
                     :bet/processed? true
                     :bet/amount     (:bet_amount dummy-guess)
                     :match/id (:match_id dummy-guess)
                     :game/id   (:game_id dummy-guess)
                     :team/id   (:team_id dummy-guess)
                     :match/name (:match_name dummy-guess)
                     :team/name      (:team_name dummy-guess)}]
                   (mapv #(dissoc % :bet/time :bet/processed-time) body)))

            (is (= [{:bet/payout     25
                     :bet/processed? true
                     :bet/amount     (:bet_amount dummy-guess-2)
                     :match/id (:match_id dummy-guess-2)
                     :game/id   (:game_id dummy-guess-2)
                     :team/id   (:team_id dummy-guess-2)
                     :match/name (:match_name dummy-guess-2)
                     :team/name      (:team_name dummy-guess-2)}
                    {:bet/payout     25
                     :bet/processed? true
                     :bet/amount     (:bet_amount dummy-guess-2)
                     :match/id (:match_id dummy-guess-2)
                     :game/id   (:game_id dummy-guess-2)
                     :team/id   (:team_id dummy-guess-2)
                     :match/name (:match_name dummy-guess-2)
                     :team/name      (:team_name dummy-guess-2)}]
                   (mapv #(dissoc % :bet/time :bet/processed-time) (:body get-guess-resp2))))

            (is (= 425 (-> (get-user auth-token) :body :user/cash)))

            #_(is (= 200 (:status leaderboard-resp)))
            #_(is (= [{:user_name "queefburglar" :payout 50}]
                   (common/parse-json-body leaderboard-resp)))

            (is (= [{:cash      425
                     :user_name "queefburglar"}]
                   (common/parse-json-body all-time-leaderboard-resp)))))))))

#_(deftest payout
  (testing "Testing more complex payout"
    (let [keys-to-select [:game/id :team/name :team/id :game/type :match/name
                          :bet/processed? :bet/amount :bet/payout :match/id]
          {:keys [auth-token] login-resp :response} (create-user-and-login)
          {auth-token2 :auth-token login-resp2 :response} (create-user-and-login dummy-user-2)
          create-guess-resp (create-bet auth-token (assoc dummy-guess :bet_amount 475))
          create-guess-resp2 (create-bet auth-token2 (assoc dummy-guess :bet_amount 100
                                                                        :team_id 125859
                                                                        :team_name "Other-team"))
          bets-resp ((common/test-app) (-> (mock/request :get "/leaderboard/bets")
                                           (mock/query-string {:match_id (:match_id dummy-guess)
                                                               :game_id  (:game_id dummy-guess)})))]

      (is (= 25 (-> (get-user auth-token) :body :user/cash)))
      (is (= 400 (-> (get-user auth-token2) :body :user/cash)))

      (is (= {:Liquid     {:bets  [{:bet/amount 475
                                    :user/name  "queefburglar"}]
                           :odds  1.210526315789474
                           :total 475}
              :Other-team {:bets  [{:bet/amount 100
                                    :user/name  "donniedarko"}]
                           :odds  5.75
                           :total 100}}
             (common/parse-json-body bets-resp)))

     (testing "Proper payout"
        (with-redefs [whiplash.integrations.pandascore/get-matches-request common/pandascore-finished-fake]
          (let [{:keys [game_id match_id]} dummy-guess
                _ (guess-processor/process-bets)
                {:keys [body] :as get-guess-resp} (get-bets auth-token game_id match_id)
                get-guess-resp2 (get-bets auth-token2 game_id match_id)
                #_#_leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/weekly")))
                all-time-leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))]

            (is (= [{:game/id        game_id
                     :team/name      (:team_name dummy-guess)
                     :team/id        (:team_id dummy-guess)
                     :match/id       match_id
                     :match/name     (:match_name dummy-guess)
                     :bet/processed? true
                     :bet/amount     475
                     :bet/payout     0}]
                   (mapv #(dissoc % :bet/time :bet/processed-time) body)))

            (is (= [{:game/id        game_id
                     :team/name      "Other-team"
                     :team/id        125859
                     :match/id       match_id
                     :match/name     (:match_name dummy-guess)
                     :bet/processed? true
                     :bet/amount     100
                     :bet/payout     575}]
                   (mapv #(dissoc % :bet/time :bet/processed-time) (:body get-guess-resp2))))

            ;; if user falls below 100, bail them out so they have 100 again
            (is (= 100 (-> (get-user auth-token) :body :user/cash)))
            (is (= 975 (-> (get-user auth-token2) :body :user/cash)))

            #_(is (= [{:user_name "donniedarko" :payout 575}]
                   (common/parse-json-body leaderboard-resp)))

            (is (= [{:cash      975
                     :user_name "donniedarko"}
                    {:cash      100
                     :user_name "queefburglar"}]
                   (common/parse-json-body all-time-leaderboard-resp)))))))))

#_(deftest fail-add-guess
  (testing "Fail to auth because no cookie"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)
          guess-resp ((common/test-app) (-> (mock/request :post "/user/guess")
                                            (mock/json-body dummy-guess)))]
      (is (= 403 (:status guess-resp))))))

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

(deftest verify-email
  (testing "verify get"
    (let [response ((common/test-app) (mock/request :get "/user/verify"))]
      (is (= 200 (:status response)))))

  (testing "verify email post"
    (let [{:keys [user_name email first_name last_name]} dummy-user
          {:keys [auth-token] login-resp :response} (create-user-and-login)
          {:keys [body] :as get-success-resp} (get-user auth-token)
          {:keys [user/verify-token user/email]} body
          verify-resp ((common/test-app) (-> (mock/request :post "/user/verify")
                                             (mock/json-body {:email email
                                                              :token verify-token})))
          try-verify-again-resp ((common/test-app) (-> (mock/request :post "/user/verify")
                                                       (mock/json-body {:email email
                                                                        :token verify-token})))
          failed-verify-resp ((common/test-app) (-> (mock/request :post "/user/verify")
                                                    (mock/json-body {:email email
                                                                     :token "you only yolo once"})))
          get-verified-user (get-user auth-token)]
      (is (= 200 (:status verify-resp)))
      (is (= {:message (format "Successfully verified %s" email)}
             (common/parse-json-body verify-resp)))

      (is (= 200 (:status try-verify-again-resp)))
      (is (= {:message (format "Already verified %s" email)}
             (common/parse-json-body try-verify-again-resp)))

      (is (= 404 (:status failed-verify-resp)))
      (is (= {:message (format "Couldn't verify %s" email)}
             (common/parse-json-body failed-verify-resp)))

      (is (not (string/blank? (-> get-verified-user :body :user/verify-token))))

      (is (= #:user{:email      email
                    :first-name first_name
                    :last-name  last_name
                    :status "user.status/active"
                    :name user_name}
             (select-keys (:body get-verified-user)
                          [:user/email :user/first-name :user/last-name :user/status :user/name]))))))

#_(deftest get-stream
  (testing "Test getting stream works, hits fixtures"
    (with-redefs [whiplash.integrations.pandascore/get-matches-request common/pandascore-running-fake
                  whiplash.integrations.twitch/views-per-twitch-stream common/twitch-view-fake]
      (let [resp ((common/test-app) (-> (mock/request :get "/stream")))
            body (common/parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (every? #(contains? body %) [:live_url :status :id :games :opponents]))
        (is (= (:live_url body) "https://player.twitch.tv/?channel=faceittv"))))))

(deftest all-time-top-ten
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
  [{:keys [auth-token text end-betting-secs]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/prop")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:text             text
                                                     :end-betting-secs (or end-betting-secs
                                                                           30)})))]
    (is (= 200 (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- admin-end-prop
  [{:keys [auth-token result]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/admin/prop/end")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:result result})))]
    (is (= 200 (:status resp)))
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
     (assoc resp :body (common/parse-json-body resp)))))

(defn- user-place-prop-bet
  [{:keys [auth-token projected-result bet-amount status]}]
  (let [resp ((common/test-app) (-> (mock/request :post "/user/prop-bet")
                                    (mock/cookie :value auth-token)
                                    (mock/json-body {:projected_result projected-result
                                                     :bet_amount       bet-amount})))]
    (is (= (or status
               200)
           (:status resp)))
    (assoc resp :body (common/parse-json-body resp))))

(defn- user-get-prop-bets
  [{:keys [auth-token]}]
  (let [resp ((common/test-app) (-> (mock/request :get "/user/prop-bet")
                                    (mock/cookie :value auth-token)))]
    (is (= 200 (:status resp)))
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
                           :text text}
             (dissoc current-prop :proposition/start-time :proposition/betting-end-time)))

      (is (string? (:event/start-time get-response-body)))
      (is (= #:event{:running? true
                     :title title
                     :stream-source "event.stream-source/twitch"
                     :channel-id twitch-user}
             (dissoc get-response-body :event/start-time))))))

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
  (testing ""
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))

          event-score-before-event-creation (get-event-leaderboard {:status 404})

          title "Dirty Dan's Delirious Dance Party"
          twitch-user "drdisrespect"
          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title title
                                                 :channel-id twitch-user})

          event-score-before-prop-creation (get-event-leaderboard {:status 404})

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

          user-get-prop-bet-resp (user-get-prop-bets {:auth-token auth-token})

          get-body (:body user-get-prop-bet-resp)

          ;; user 3 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-3)

          user-place-prop-bet-resp3 (user-place-prop-bet {:auth-token auth-token
                                                          :projected-result false
                                                          :bet-amount 100})
          user-place-prop-bet-resp4 (user-place-prop-bet {:auth-token auth-token
                                                          :projected-result true
                                                          :bet-amount 400})

          user-get-prop-bet-resp2 (user-get-prop-bets {:auth-token auth-token})
          get-body2 (:body user-get-prop-bet-resp2)

          current-prop-bets-response  (get-prop-bets-leaderboard)

          event-score-before-prop-result (get-event-leaderboard)
          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                             :result true})

          event-score-before-end (get-event-leaderboard)
          ;;admin didnt bet
          _ (is (= 500 (-> (get-user auth-token) :body :user/cash)))

          {:keys [auth-token] login-resp :response} (login dummy-user-2)
          _ (is (= 333 (-> (get-user auth-token) :body :user/cash)))

          {:keys [auth-token] login-resp :response} (login dummy-user-3)
          _ (is (= 666 (-> (get-user auth-token) :body :user/cash)))

          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          end-event-resp (admin-end-event {:auth-token auth-token})

          event-score-after-end (get-event-leaderboard)]

      (is (= [#:bet{:amount            200
                    :projected-result? true}
              #:bet{:amount            300
                    :projected-result? false}]
             (mapv #(dissoc % :bet/time) get-body)))

      (is (= [#:bet{:amount            100
                    :projected-result? false}
              #:bet{:amount            400
                    :projected-result? true}]
             (->> get-body2
                  (mapv #(dissoc % :bet/time))
                  (sort-by :bet/amount))))

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

      (is (= []
             (:body event-score-before-event-creation)
             (:body event-score-before-prop-creation)))

      (is (= [{:score     0
               :user_name "donniedarko"}
              {:score     0
               :user_name "kittycuddler420"}]
             (:body event-score-before-prop-result)))

      (is (= [{:score     166
               :user_name "kittycuddler420"}
              {:score     -167
               :user_name "donniedarko"}]
             (:body event-score-before-end)
             (:body event-score-after-end))))))

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
          _ (is (= 100 (-> (get-user auth-token) :body :user/cash)))

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

          _ (is (= 500 (-> (get-user auth-token) :body :user/cash)))

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
             (common/parse-json-body all-time-leaderboard-end))))))

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
