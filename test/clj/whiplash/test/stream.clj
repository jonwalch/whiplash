(ns whiplash.test.stream
  (:require [clojure.test :refer :all]
            [whiplash.handler :as handler]
            [ring.mock.request :as mock]
            [whiplash.test.common :as common]))

(common/app-fixtures)
(common/db-fixtures)

(deftest get-stream
    (testing "this test currently makes network calls that have quotas, don't run it often"
      (let [resp ((handler/app) (-> (mock/request :get "/v1/stream")
                                    ))
            body (common/parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (every? some? (select-keys body [:live_url :status :id :games :opponents])))
        (println resp)
        (println (keys body)))))
