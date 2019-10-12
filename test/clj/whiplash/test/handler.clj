(ns whiplash.test.handler
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :as mock]
    [whiplash.handler :as handler]
    [whiplash.middleware.formats :as formats]
    [muuntaja.core :as m]
    [mount.core :as mount]
    [clojure.string :as string]))

(defn parse-json-body
  [{:keys [body] :as req}]
  ;(assert body)
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'whiplash.config/env
                 #'whiplash.handler/app-routes)
    (f)))

(use-fixtures
  :each
  (fn [f]
    (mount/start #'whiplash.db.core/conn)
    (f)
    (mount/stop #'whiplash.db.core/conn)))

;; TODO: Setup running these tests automatically in AWS CodeBuild
;; Planning on going with AWS because Datomic support is very good

(deftest test-app
  (testing "main route"
    (let [response ((handler/app) (mock/request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((handler/app) (mock/request :get "/invalid"))]
      (is (= 404 (:status response)))))

  (testing "services"
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
          ((handler/app) (mock/request :get "/js/index.tsx"))]
      (is (= 200 status)))))

(def dummy-user
  {:first-name "yas"
   :last-name "queen"
   :email "butt@cheek.com"
   :password "foobar"
   :screen-name "queefburglar"})

(deftest test-user-400s
  (testing "get fail spec"
    (let [response ((handler/app) (mock/request :get "/v1/user/login"))]
      (is (= 400 (:status response)))))

  (testing "cant get user, not logged in"
    (let [response ((handler/app) (-> (mock/request :get "/v1/user/login")
                                      (mock/query-string {:email "kanye@west.com"})))]
      (is (= 403 (:status response)))))

  (testing "post create user failure"
    (let [{:keys [status] :as response}
          ((handler/app) (-> (mock/request :post "/v1/user/create")
                             (mock/json-body {:shit "yas"})))]
      (is (= 400 status))))

  (testing "can't login as nonexistent user"
    (let [login-resp ((handler/app) (-> (mock/request :post "/v1/user/login")
                                        (mock/json-body {:email    (:email dummy-user)
                                                         :password (:email dummy-user)})))]
      (is (= 401 (:status login-resp))))))

(defn get-token-from-headers
  [headers]
  (->> (get headers "Set-Cookie")
       (filter #(string/includes? % "value="))
       first
       (re-find #"^value=(.*)$")
       second))

(defn- create-user
  ([]
   (create-user dummy-user))
  ([user]
   (assert user)
   (let [resp ((handler/app) (-> (mock/request :post "/v1/user/create")
                                             (mock/json-body dummy-user)))
         parsed-body (parse-json-body resp)]

     (is (= 200 (:status resp)))
     (is (nil? parsed-body))

     (assoc resp :body parsed-body))))

(defn- login
  ([]
   (login dummy-user))
  ([{:keys [email password] :as user}]
   (assert (and email password))
   (let [resp ((handler/app) (-> (mock/request :post "/v1/user/login")
                                       (mock/json-body {:email    email
                                                        :password password})))
         parsed-body (parse-json-body resp)
         auth-token (-> resp :headers get-token-from-headers)]

     ;auth-token (some->> login-resp parse-json-body :auth-token (str "Bearer "))

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

(defn- get-user
  ([auth-token]
   (get-user dummy-user auth-token))
  ([{:keys [email first-name last-name screen-name] :as user} auth-token]
   (let [resp ((handler/app) (-> (mock/request :get "/v1/user/login")
                                 (mock/query-string {:email email})
                                 (mock/cookie :value auth-token)
                                 #_(mock/header "Authorization" (str "Bearer " auth-token))))
         parsed-body (parse-json-body resp)]

     (is (= 200 (:status resp)))
     (is (= #:user{:email      email
                   :first-name first-name
                   :last-name  last-name
                   :status "user.status/pending"
                   :screen-name screen-name}
            parsed-body))

     (assoc resp :body parsed-body))))

(deftest test-user
  (testing "create and get user success "
    (let [{:keys [email]} dummy-user
          {:keys [auth-token] login-resp :response} (create-user-and-login)
          login-fail-resp ((handler/app) (-> (mock/request :post "/v1/user/login")
                                             (mock/json-body {:email email
                                                              :password "wrong_password"})))
          get-fail-resp ((handler/app) (-> (mock/request :get "/v1/user/login")
                                           (mock/query-string {:email email})))

          get-success-resp (get-user auth-token)]

      (is (= 401 (:status login-fail-resp)))
      (is (= 403 (:status get-fail-resp))))))

(defn dummy-guess
  [user]
  (assoc
    {:game-type "csgo"
     :game-name "Jon's Dangus Squad Vs. Peter's Pumpkin Eaters Game 3"
     :game-id   123
     :team-name "Peter's Pumpkin Eaters"
     :team-id   2}
    :screen-name (:screen-name user)))

(defn dummy-guess-2
  [user]
  (assoc
    {:game-type "csgo"
     :game-name "Jon's Dangus Squad Vs. Peter's Pumpkin Eaters Game 4"
     :game-id   124
     :team-name "Peter's Pumpkin Eaters"
     :team-id   2}
    :screen-name (:screen-name user)))

(defn- create-guess
  ([auth-token guess]
   (create-guess dummy-user auth-token guess))
  ([user auth-token guess]
   (let [resp ((handler/app) (-> (mock/request :post "/v1/user/guess")
                                 (mock/json-body guess)
                                 (mock/cookie :value auth-token)))
         parsed-body (parse-json-body resp)]
     (is (= 200 (:status resp)))

     (assoc resp :body parsed-body))))


(defn- get-guess
  ([auth-token screen-name game-id]
   (get-guess dummy-user auth-token screen-name game-id))
  ([user auth-token screen-name game-id]
   (let [resp ((handler/app) (-> (mock/request :get "/v1/user/guess")
                                 (mock/query-string {:screen-name screen-name
                                                     :game-id game-id})
                                 (mock/cookie :value auth-token)))
         parsed-body (parse-json-body resp)]
     (is (= 200 (:status resp)))

     (assoc resp :body parsed-body))))

;; TODO check db that it exists how we expect
;; TODO add test to check fialure when not authed
(deftest add-guesses
  (testing "We can add and get guesses for a user"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)
          {:keys [screen-name game-id] :as dummy-guess} (dummy-guess dummy-user)
          dummy-guess2 (dummy-guess-2 dummy-user)
          create-guess-resp (create-guess auth-token dummy-guess)
          create-guess-resp2 (create-guess auth-token dummy-guess2)
          {:keys [body] :as get-guess-resp} (get-guess auth-token screen-name game-id)
          get-guess-resp2 (get-guess auth-token
                                     (:screen-name dummy-guess2)
                                     (:game-id dummy-guess2))]
      (is (= {;;:guess/time "2019-10-12T21:23:47Z"
              :game/id 123
              :team/name "Peter's Pumpkin Eaters"
              :team/id 2
              :game/type "game.type/csgo"
              :game/name "Jon's Dangus Squad Vs. Peter's Pumpkin Eaters Game 3"}
             (select-keys body
                          [:game/id :team/name :team/id :game/type :game/name])))
      (is (= {;;:guess/time "2019-10-12T21:23:47Z"
              :game/id 124
              :team/name "Peter's Pumpkin Eaters"
              :team/id 2
              :game/type "game.type/csgo"
              :game/name "Jon's Dangus Squad Vs. Peter's Pumpkin Eaters Game 4"}
             (select-keys (:body get-guess-resp2)
                          [:game/id :team/name :team/id :game/type :game/name])))))

  (testing "Fail to auth because no cookie"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)
          guess-resp ((handler/app) (-> (mock/request :post "/v1/user/guess")
                                        (mock/json-body (dummy-guess dummy-user))))]
      (is (= 403 (:status guess-resp))))))
