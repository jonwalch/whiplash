(ns whiplash.test.handler
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :as mock]
    [clojure.string :as string]
    [whiplash.test.common :as common]
    [whiplash.guess-processor :as guess-processor]
    [whiplash.db.core :as db]
    [datomic.client.api :as d]))

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

      (is (= 401 (:status login-resp)))))

  (testing "not authed"
    (let [response ((common/test-app) (-> (mock/request :get "/user/login")))]
      (is (= 403 (:status response))))))

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
  ([{:keys [first_name email] :as user}]
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
         auth-token (-> resp :headers get-token-from-headers)

         authed-resp ((common/test-app) (-> (mock/request :get "/user/login")
                                            (mock/cookie :value auth-token)))]

     (is (= 200 (:status resp)))
     (is (string? auth-token))

     (is (= 200 (:status authed-resp)))
     (is (= #:user{:name user_name}
            (common/parse-json-body authed-resp)))

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

(def dummy-guess
  {;;:game-type "csgo"
   :match_name "Grand Final: Liquid vs ATK"
   :game_id   9388
   :match_id 549829
   :team_name "Liquid"
   :team_id   3213
   :bet_amount 75})

(def dummy-guess-2
  {;;:game-type "csgo"
   :match_name "Grand Final: Liquid vs ATK"
   :game_id   9389
   :match_id 549829
   :team_name "Liquid"
   :team_id   3213
   :bet_amount 25})

(defn- create-bet
  [auth-token guess]
  (let [resp ((common/test-app) (-> (mock/request :post "/user/guess")
                                    (mock/json-body guess)
                                    (mock/cookie :value auth-token)))
        parsed-body (common/parse-json-body resp)]
    (is (= 200 (:status resp)))

    (assoc resp :body parsed-body)))

(defn- get-bets
  [auth-token game-id match-id]
  (let [resp ((common/test-app) (-> (mock/request :get "/user/guess")
                                    (mock/query-string {:match_id match-id
                                                        :game_id  game-id})
                                    (mock/cookie :value auth-token)))
        parsed-body (common/parse-json-body resp)]
    (is (= 200 (:status resp)))

    (assoc resp :body parsed-body)))

(deftest add-guesses
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
                leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/weekly")))
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

            (is (= 200 (:status leaderboard-resp)))
            (is (= [{:user_name "queefburglar" :payout 50}]
                   (common/parse-json-body leaderboard-resp)))

            (is (= [{:cash      425
                     :user_name "queefburglar"}]
                   (common/parse-json-body all-time-leaderboard-resp)))))))))

(deftest payout
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
                leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/weekly")))
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

            (is (= [{:user_name "donniedarko" :payout 575}]
                   (common/parse-json-body leaderboard-resp)))

            (is (= [{:cash      975
                     :user_name "donniedarko"}
                    {:cash      100
                     :user_name "queefburglar"}]
                   (common/parse-json-body all-time-leaderboard-resp)))))))))

(deftest fail-add-guess
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
      (is (= "deleted" (get-token-from-headers (:headers resp))))
      (is (= 200 (:status resp))))))

(deftest logout-get
  (testing "doing a get to login after logging out doesn't 500"
    (let [{:keys [auth-token] :as login-resp} (create-user-and-login)
          resp ((common/test-app) (-> (mock/request :post "/user/logout")
                                      (mock/cookie :value auth-token)))
          get-login-resp ((common/test-app) (-> (mock/request :get "/user/login")))]
      (is (= 403 (:status get-login-resp))))))

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

(deftest get-stream
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

(deftest fail-admin-create-event
  (testing "fail to create event because not admin"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)
          resp ((common/test-app) (-> (mock/request :post "/admin/event")
                                      (mock/cookie :value auth-token)
                                      (mock/json-body {:title "poops"
                                                       :twitch-user "pig boops"})))]
      (is (= 403 (:status resp))))))

(deftest success-admin-create-event
  (testing "successfully create and get event with proper admin role"
    (let [_ (create-user)
          _ (d/transact (:conn db/datomic-cloud)
                        {:tx-data [{:db/id       (db/find-user-by-email (:email dummy-user))
                                    :user/status :user.status/admin}]})
          {:keys [auth-token] login-resp :response} (login)
          title "Dirty Dan's Delirious Dance Party"
          twitch-user "drdisrespect"
          resp ((common/test-app) (-> (mock/request :post "/admin/event")
                                      (mock/cookie :value auth-token)
                                      (mock/json-body {:title title
                                                       :twitch-user twitch-user})))
          fail-create-again-resp ((common/test-app) (-> (mock/request :post "/admin/event")
                                      (mock/cookie :value auth-token)
                                      (mock/json-body {:title title
                                                       :twitch-user twitch-user})))
          get-response ((common/test-app) (-> (mock/request :get "/admin/event")
                                              (mock/cookie :value auth-token)))
          get-response-body (common/parse-json-body get-response)

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp ((common/test-app) (-> (mock/request :post "/admin/prop")
                                                      (mock/cookie :value auth-token)
                                                      (mock/json-body {:text text})))

          success-get-running-prop-resp  ((common/test-app) (-> (mock/request :get "/admin/prop")
                                                                (mock/cookie :value auth-token)))

          success-prop-body (common/parse-json-body success-get-running-prop-resp)

          fail-end-event-resp ((common/test-app) (-> (mock/request :post "/admin/event/end")
                                                     (mock/cookie :value auth-token)))

          end-prop-bet-resp ((common/test-app) (-> (mock/request :post "/admin/prop/end")
                                                  (mock/cookie :value auth-token)
                                                  (mock/json-body {:result true})))

          failure-get-running-prop-resp  ((common/test-app) (-> (mock/request :get "/admin/prop")
                                                                (mock/cookie :value auth-token)))

          end-event-resp ((common/test-app) (-> (mock/request :post "/admin/event/end")
                                                (mock/cookie :value auth-token)))
          get-after-end-resp ((common/test-app) (-> (mock/request :get "/admin/event")
                                                    (mock/cookie :value auth-token)))]
      (is (= 200 (:status resp)))
      (is (= 405 (:status fail-create-again-resp)))
      (is (= 200 (:status get-response)))
      (is (= 200 (:status create-prop-bet-resp)))
      (is (= 200 (:status success-get-running-prop-resp)))
      (is (= 405 (:status fail-end-event-resp)))
      (is (= 200 (:status end-prop-bet-resp)))
      (is (= 404 (:status failure-get-running-prop-resp)))
      (is (= 200 (:status end-event-resp)))
      (is (= 404 (:status get-after-end-resp)))

      (is (string? (:proposition/start-time success-prop-body)))
      (is (= #:proposition{:running? true
                           :text text}
             (dissoc success-prop-body :proposition/start-time)))

      (is (string? (:event/start-time get-response-body)))
      (is (= #:event{:running? true
                     :title title
                     :twitch-user twitch-user}
             (dissoc get-response-body :event/start-time))))))

(deftest fail-admin-get-event
  (testing "fail to create event because not admin"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)
          resp ((common/test-app) (-> (mock/request :get "/admin/event")
                                      (mock/cookie :value auth-token)))]
      (is (= 403 (:status resp))))))

(deftest success-admin-get-event
  (testing "successfully get nonexistent event with proper admin role"
    (let [_ (create-user)
          _ (d/transact (:conn db/datomic-cloud)
                        {:tx-data [{:db/id       (db/find-user-by-email (:email dummy-user))
                                    :user/status :user.status/admin}]})
          {:keys [auth-token] login-resp :response} (login)
          resp ((common/test-app) (-> (mock/request :get "/admin/event")
                                      (mock/cookie :value auth-token)))]
      (is (= 404 (:status resp))))))

(deftest fail-end-event
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login)
        resp ((common/test-app) (-> (mock/request :post "/admin/event/end")
                                    (mock/cookie :value auth-token)))]
    (is (= 403 (:status resp)))))

(deftest no-event-to-end
  (let [_ (create-user)
        _ (d/transact (:conn db/datomic-cloud)
                      {:tx-data [{:db/id       (db/find-user-by-email (:email dummy-user))
                                  :user/status :user.status/admin}]})
        {:keys [auth-token] login-resp :response} (login)
        resp ((common/test-app) (-> (mock/request :post "/admin/event/end")
                                    (mock/cookie :value auth-token)))]
    (is (= 405 (:status resp)))))

(deftest admin-and-user-create-prop-bet
  (testing ""
    (let [_ (create-user)
          _ (d/transact (:conn db/datomic-cloud)
                        {:tx-data [{:db/id       (db/find-user-by-email (:email dummy-user))
                                    :user/status :user.status/admin}]})
          {:keys [auth-token] login-resp :response} (login)

          title "Dirty Dan's Delirious Dance Party"
          twitch-user "drdisrespect"
          create-event-resp ((common/test-app) (-> (mock/request :post "/admin/event")
                                                   (mock/cookie :value auth-token)
                                                   (mock/json-body {:title       title
                                                                    :twitch-user twitch-user})))

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp ((common/test-app) (-> (mock/request :post "/admin/prop")
                                                      (mock/cookie :value auth-token)
                                                      (mock/json-body {:text text})))

          _ (create-user dummy-user-2)
          _ (create-user dummy-user-3)

          ;; user 2 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-2)


          user-place-prop-bet-resp ((common/test-app) (-> (mock/request :post "/user/prop-bet")
                                                          (mock/cookie :value auth-token)
                                                          (mock/json-body {:projected_result true
                                                                           :bet_amount       200})))
          user-place-prop-bet-resp2 ((common/test-app) (-> (mock/request :post "/user/prop-bet")
                                                           (mock/cookie :value auth-token)
                                                           (mock/json-body {:projected_result false
                                                                            :bet_amount       300})))
          user-get-prop-bet-resp ((common/test-app) (-> (mock/request :get "/user/prop-bet")
                                                        (mock/cookie :value auth-token)))

          get-body (common/parse-json-body user-get-prop-bet-resp)


          ;; user 3 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-3)


          user-place-prop-bet-resp3 ((common/test-app) (-> (mock/request :post "/user/prop-bet")
                                                           (mock/cookie :value auth-token)
                                                           (mock/json-body {:projected_result false
                                                                            :bet_amount       100})))
          user-place-prop-bet-resp4 ((common/test-app) (-> (mock/request :post "/user/prop-bet")
                                                           (mock/cookie :value auth-token)
                                                           (mock/json-body {:projected_result true
                                                                            :bet_amount       400})))
          user-get-prop-bet-resp2 ((common/test-app) (-> (mock/request :get "/user/prop-bet")
                                                         (mock/cookie :value auth-token)))

          get-body2 (common/parse-json-body user-get-prop-bet-resp2)

          current-prop-bets-response  ((common/test-app) (-> (mock/request :get "/leaderboard/prop-bets")))

          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp ((common/test-app) (-> (mock/request :post "/admin/prop/end")
                                                   (mock/cookie :value auth-token)
                                                   (mock/json-body {:result true})))

          ;;admin didnt bet
          _ (is (= 500 (-> (get-user auth-token) :body :user/cash)))

          {:keys [auth-token] login-resp :response} (login dummy-user-2)
          _ (is (= 333 (-> (get-user auth-token) :body :user/cash)))

          {:keys [auth-token] login-resp :response} (login dummy-user-3)
          _ (is (= 666 (-> (get-user auth-token) :body :user/cash)))

          weekly-leader-resp  ((common/test-app) (-> (mock/request :get "/leaderboard/weekly-prop-bets")))

          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          resp ((common/test-app) (-> (mock/request :post "/admin/event/end")
                                      (mock/cookie :value auth-token)))]

      (is (= 200 (:status create-event-resp)))
      (is (= 200 (:status create-prop-bet-resp)))
      (is (= 200 (:status user-place-prop-bet-resp)))
      (is (= 200 (:status user-place-prop-bet-resp2)))

      (is (= 200 (:status user-get-prop-bet-resp)))
      (is (= [#:bet{:amount            200
                    :projected-result? true}
              #:bet{:amount            300
                    :projected-result? false}]
             (mapv #(dissoc % :bet/time) get-body)))

      (is (= 200 (:status user-place-prop-bet-resp3)))
      (is (= 200 (:status user-place-prop-bet-resp4)))

      (is (= 200 (:status user-get-prop-bet-resp2)))
      (is (= [#:bet{:amount            100
                    :projected-result? false}
              #:bet{:amount            400
                    :projected-result? true}]
             (mapv #(dissoc % :bet/time) get-body2)))

      (is (= 200 (:status current-prop-bets-response)))
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
             (common/parse-json-body current-prop-bets-response)))

      (is (= 200 (:status weekly-leader-resp)))
      (is (= [{:payout    666
               :user_name "kittycuddler420"}
              {:payout    333
               :user_name "donniedarko"}]
             (common/parse-json-body weekly-leader-resp)))


      (is (= 200 (:status end-prop-bet-resp)))
      (is (= 200 (:status resp))))))
