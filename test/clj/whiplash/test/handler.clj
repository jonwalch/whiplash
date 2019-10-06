(ns whiplash.test.handler
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :as mock]
    [whiplash.handler :as handler]
    [whiplash.middleware.formats :as formats]
    [muuntaja.core :as m]
    [mount.core :as mount]))

(defn parse-json-body
  [{:keys [body] :as req}]
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

(def dummy-user
  {:first-name "yas"
   :last-name "queen"
   :email "butt@cheek.com"
   :password "foobar"})

(deftest test-user
  (testing "get fail spec"
    (let [response ((handler/app) (mock/request :get "/v1/user/login"))]
      (is (= 400 (:status response)))))

  (testing "get user doesn't exist"
    (let [response ((handler/app) (-> (mock/request :get "/v1/user/login")
                                      (mock/query-string {:email "kanye@west.com"})))]
      (is (= 404 (:status response)))))

  (testing "post create user failure"
    (let [{:keys [status] :as response}
          ((handler/app) (-> (mock/request :post "/v1/user/create")
                             (mock/json-body {:shit "yas"})))]
      (is (= 400 status))))

  (testing "create and get user success "
    (let [{:keys [email first-name last-name password]} dummy-user
          {:keys [status] :as response}
          ((handler/app) (-> (mock/request :post "/v1/user/create")
                             (mock/json-body dummy-user)))
          login-response ((handler/app) (-> (mock/request :post "/v1/user/login")
                                            (mock/json-body {:email email
                                                             :password password})))
          get-response ((handler/app) (-> (mock/request :get "/v1/user/login")
                                          (mock/query-string {:email email})))]
      (is (= 200 status))
      (is (nil? (parse-json-body response)))

      (is (= 200 (:status login-response)))
      (is (= {:auth-token "token"}
             (parse-json-body login-response)))

      (is (= 200 (:status get-response)))
      (is (= #:user{:email      email
                    :first-name first-name
                    :last-name  last-name
                    :status "user.status/pending"}
             (parse-json-body get-response))))))
