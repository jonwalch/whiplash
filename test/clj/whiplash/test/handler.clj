(ns whiplash.test.handler
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :as mock]
    [whiplash.handler :as handler]
    [whiplash.middleware.formats :as formats]
    [muuntaja.core :as m]
    [mount.core :as mount]))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'whiplash.config/env
                 #'whiplash.handler/app-routes)
    (f)))

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
      (let [response ((handler/app) (-> (mock/request :post "/api/math/plus")
                                        (json-body {:x 10, :y 6})))]
        (is (= 200 (:status response)))
        (is (= {:total 16} (m/decode-response-body response)))))

    (testing "parameter coercion error, stack trace expected"
      (let [response ((handler/app) (-> (mock/request :post "/api/math/plus")
                                        (json-body {:x 10, :y "invalid"})))]
        (is (= 400 (:status response)))))

    (testing "response coercion error"
      (let [response ((handler/app) (-> (mock/request :post "/api/math/plus")
                                        (json-body {:x -10, :y 6})))]
        (is (= 500 (:status response)))))

    (testing "content negotiation"
      (let [response ((handler/app) (-> (mock/request :post "/api/math/plus")
                                        (body (pr-str {:x 10, :y 6}))
                                        (content-type "application/edn")
                                        (header "accept" "application/transit+json")))]
        (is (= 200 (:status response)))
        (is (= {:total 16} (m/decode-response-body response)))))))
