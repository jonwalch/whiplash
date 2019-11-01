(ns whiplash.test.handler
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :as mock]
    [clojure.string :as string]
    [whiplash.test.common :as common]
    [whiplash.guess-processor :as guess-processor]))

(common/app-fixtures)
(common/db-fixtures)

;; TODO: Setup running these tests automatically in AWS CodeBuild
(deftest test-app
  (testing "main route"
    (let [response ((common/test-app) (mock/request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((common/test-app) (mock/request :get "/invalid"))]
      (is (= 404 (:status response)))))

  #_(testing "services"
      (testing "success"
        (let [response ((handler/app) (-> (mock/request :post "/v1/math/plus")
                                          (mock/json-body {:x 10, :y 6})))]
          (is (= 200 (:status response)))
          (is (= {:total 16} (m/decode-response-body response)))))

      (testing "parameter coercion error, stack trace expected"
        (let [response ((handler/app) (-> (mock/request :post "/v1/math/plus")
                                          (mock/json-body {:x 10, :y "invalid"})))]
          (is (= 400 (:status response)))))

      (testing "parameter coercion error, stack trace expected"
        (let [response ((handler/app) (-> (mock/request :get "/v1/math/plus")
                                          (mock/query-string {:x 10, :y "invalid"})))]
          (is (= 400 (:status response)))))

      (testing "response coercion error"
        (let [response ((handler/app) (-> (mock/request :post "/v1/math/plus")
                                          (mock/json-body {:x -10, :y 6})))]
          (is (= 500 (:status response)))))

      (testing "fail spec"
        (let [response ((handler/app) (-> (mock/request :post "/v1/math/plus")
                                          (mock/json-body {:piss "fart"})))]
          (is (= 400 (:status response)))))

      (testing "content negotiation"
        (let [response ((handler/app) (-> (mock/request :post "/v1/math/plus")
                                          (mock/body (pr-str {:x 10, :y 6}))
                                          (mock/content-type "application/edn")
                                          (mock/header "accept" "application/transit+json")))]
          (is (= 200 (:status response)))
          (is (= {:total 16} (m/decode-response-body response)))))))

(deftest static-content
  (testing "can get static content"
    (let [{:keys [status] :as response}
          ((common/test-app) (mock/request :get "/js/index.tsx"))]
      (is (= 200 status)))))

(def dummy-user
  {:first_name "yas"
   :last_name "queen"
   :email "butt@cheek.com"
   :password "foobar2000"
   :user_name "queefburglar"})

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
  ([user]
   (assert user)
   (let [resp ((common/test-app) (-> (mock/request :post "/user/create")
                                     (mock/json-body user)))
         parsed-body (common/parse-json-body resp)]

     (is (= 200 (:status resp)))
     (is (empty? parsed-body))

     (assoc resp :body parsed-body))))

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
                                                                  :password "wrong_password"})))
          get-success-resp (get-user auth-token)
          create-again-fail ((common/test-app) (-> (mock/request :post "/user/create")
                                                   (mock/json-body dummy-user)))]

      (is (= #:user{:email      email
                    :first-name first_name
                    :last-name  last_name
                    :status "user.status/pending"
                    :name user_name
                    :verify-token ""}
             (:body get-success-resp)))
      (is (= 401 (:status login-fail-resp)))
      (is (= 409 (:status create-again-fail))))))

(def dummy-guess
  {;;:game-type "csgo"
   :match_name "Grand Final: Liquid vs ATK"
   :game_id   9388
   :match_id 549829
   :team_name "Liquid"
   :team_id   3213})

(def dummy-guess-2
  {;;:game-type "csgo"
   :match_name "Grand Final: Liquid vs ATK"
   :game_id   9389
   :match_id 549829
   :team_name "Liquid"
   :team_id   3213})

(defn- create-guess
  [auth-token guess]
  (let [resp ((common/test-app) (-> (mock/request :post "/user/guess")
                                    (mock/json-body guess)
                                    (mock/cookie :value auth-token)))
        parsed-body (common/parse-json-body resp)]
    (is (= 200 (:status resp)))

    (assoc resp :body parsed-body)))

(defn- get-guess
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
    (let [keys-to-select [:game/id :team/name :team/id :game/type :match/name :guess/processed? :guess/score]
          {:keys [auth-token] login-resp :response} (create-user-and-login)
          {:keys [game_id match_id]} dummy-guess
          create-guess-resp (create-guess auth-token dummy-guess)
          create-guess-resp2 (create-guess auth-token dummy-guess-2)
          {:keys [body] :as get-guess-resp} (get-guess auth-token game_id match_id)
          get-guess-resp2 (get-guess auth-token
                                     (:game_id dummy-guess-2)
                                     (:match_id dummy-guess-2))
          fail-create-same-guess-resp ((common/test-app) (-> (mock/request :post "/user/guess")
                                                             (mock/json-body dummy-guess)
                                                             (mock/cookie :value auth-token)))]
      (is (= {:game/id   (:game_id dummy-guess)
              :team/name (:team_name dummy-guess)
              :team/id   (:team_id dummy-guess)
              :game/type "game.type/csgo"
              :match/name (:match_name dummy-guess)
              :guess/processed? false}
             (select-keys body keys-to-select)))

      (is (= {:game/id   (:game_id dummy-guess-2)
              :team/name (:team_name dummy-guess-2)
              :team/id   (:team_id dummy-guess-2)
              :game/type "game.type/csgo"
              :match/name (:match_name dummy-guess-2)
              :guess/processed? false}
             (select-keys (:body get-guess-resp2) keys-to-select)))

      (is (not= (:guess/time body)
                (:guess/time get-guess-resp2)))

      (is (= nil
             (:guess/processed-time body)
             (:guess/processed-time get-guess-resp2)))

      (is (= 409 (:status fail-create-same-guess-resp)))

      (testing "Guess success processing works"
        (with-redefs [whiplash.integrations.pandascore/get-matches-request common/pandascore-finished-fake
                      whiplash.integrations.twitch/views-per-twitch-stream common/twitch-view-fake]
          (let [_ (guess-processor/process-guesses)
                {:keys [body] :as get-guess-resp} (get-guess auth-token game_id match_id)
                get-guess-resp2 (get-guess auth-token
                                           (:game_id dummy-guess-2)
                                           (:match_id dummy-guess-2))
                leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/weekly")))]

            (is (= {:game/id          (:game_id dummy-guess)
                    :team/name        (:team_name dummy-guess)
                    :team/id          (:team_id dummy-guess)
                    :game/type        "game.type/csgo"
                    :match/name       (:match_name dummy-guess)
                    :guess/processed? true
                    :guess/score      0}
                   (select-keys body keys-to-select)))

            (is (= {:game/id          (:game_id dummy-guess-2)
                    :team/name        (:team_name dummy-guess-2)
                    :team/id          (:team_id dummy-guess-2)
                    :game/type        "game.type/csgo"
                    :match/name       (:match_name dummy-guess-2)
                    :guess/processed? true
                    :guess/score      100}
                   (select-keys (:body get-guess-resp2) keys-to-select)))

            (is (not= (:guess/processed-time body)
                      (:guess/processed-time get-guess-resp2)))

            (is (= 200 (:status leaderboard-resp)))
            (is (= [{:user_name "queefburglar" :score 100}]
                   (common/parse-json-body leaderboard-resp)))))))))

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
  (is (= "Password invalid"
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
