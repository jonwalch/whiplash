(ns whiplash.test.handler
  (:require
    [clojure.test :refer :all]
    [whiplash.test.helpers :refer :all]
    [ring.mock.request :as mock]
    [clojure.string :as string]
    [whiplash.test.common :as common]
    [whiplash.db.core :as db]
    [datomic.client.api :as d]
    [clj-uuid :as uuid]
    [whiplash.time :as time]
    [whiplash.event-manager :as em]))

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

  #_(testing "nba landing"
    (let [response ((common/test-app) (mock/request :get "/nba"))]
      (is (= 200 (:status response)))))

  (testing "account"
    (let [response ((common/test-app) (mock/request :get "/account"))]
      (is (= 200 (:status response)))))

  (testing "live"
    (let [response ((common/test-app) (mock/request :get "/live"))]
      (is (= 200 (:status response)))))

  (testing "user page"
    (let [response ((common/test-app) (mock/request :get "/u/donnie"))]
      (is (= 200 (:status response)))))

  #_(testing "leaderboard"
    (let [response ((common/test-app) (mock/request :get "/leaderboard"))]
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
                                                             :password  (:password dummy-user)})))]

      (is (= 401 (:status login-resp))))))

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

      (is (= #:user{:email         email
                    :first-name    first_name
                    :last-name     last_name
                    :status        "user.status/pending"
                    :gated?        false
                    :id            "3b6f25e60ca93d61357f0d8af982fdbe2eb6fa2ec65d0693d50567a76ac43141"
                    :name          user_name
                    :cash          500
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
          :password  (:password dummy-user)}))

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
  (is (= "User name invalid"
         (-> (create-user-failure (assoc dummy-user :user_name "donnie darko"))
             :body
             :message)))
  (is (= "User name invalid"
         (-> (create-user-failure (assoc dummy-user :user_name " "))
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
          verify-resp (verify-email {:email        email
                                     :verify-token verify-token})
          try-verify-again-resp (verify-email {:email        email
                                               :verify-token verify-token})
          failed-verify-resp (verify-email {:email        email
                                            :verify-token "you only yolo once"
                                            :status       404})
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
                    :id            "3b6f25e60ca93d61357f0d8af982fdbe2eb6fa2ec65d0693d50567a76ac43141"
                    :name          "queefburglar"
                    :notifications []
                    :gated?        false
                    :status        "user.status/active"}
             (:body get-verified-user))))))

(deftest all-time-top-25-test
  (testing "only returns 25 users"
    (doseq [x (range 27)]
      (create-user (assoc dummy-user :email (str x "@poops.com") :user_name (str x))))
    (let [all-time-leaderboard-resp ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))]
      (is (= 25
             (count (common/parse-json-body all-time-leaderboard-resp)))))))

(deftest options-event-leaderboard
  (testing "need this endpoint and headers for CORS (twitch extension)"
    (let [resp ((common/test-app) (-> (mock/request :options "/leaderboard/event/foo")))]
      (is (= 204
             (:status resp)))
      (is (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID, X-Twitch-User-ID"
              "Access-Control-Allow-Methods" "GET"
              "Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
              "Cache-Control"                "max-age=86400"
              "Content-Type"                 "application/octet-stream"
              "X-Content-Type-Options"       "nosniff"
              "X-Frame-Options"              "SAMEORIGIN"
              "X-XSS-Protection"             "1; mode=block"}
             (:headers resp))))))

(deftest options-proposition
  (testing "need this endpoint and headers for CORS (twitch extension)"
    (let [resp ((common/test-app) (-> (mock/request :options "/stream/prop/foo")))]
      (is (= 204
             (:status resp)))
      (is (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID, X-Twitch-User-ID"
              "Access-Control-Allow-Methods" "GET"
              "Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
              "Cache-Control"                "max-age=86400"
              "Content-Type"                 "application/octet-stream"
              "X-Content-Type-Options"       "nosniff"
              "X-Frame-Options"              "SAMEORIGIN"
              "X-XSS-Protection"             "1; mode=block"}
             (:headers resp))))))

(deftest fail-admin-create-event
  (testing "fail to create event because not admin"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)]
      (admin-create-event {:auth-token auth-token
                           :title      "poops"
                           :channel-id "pig boops"
                           :status     403}))))

(deftest success-admin-create-event
  (testing "successfully create and get event with proper admin role"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          title "Dirty Dan's Delirious Dance Party"
          channel-id "Drdisrespect"
          resp (admin-create-event {:auth-token auth-token
                                    :title      title
                                    :channel-id channel-id})
          ;;cannot create another event with same string in different case
          fail-create-again-resp (admin-create-event {:auth-token auth-token
                                                      :title      title
                                                      :channel-id (string/lower-case channel-id)
                                                      :status     405})

          get-event-response (get-event {:channel-id channel-id})
          get-response-body (:body get-event-response)

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text       text
                                                   :channel-id channel-id})

          success-get-running-prop-resp (get-prop {:channel-id channel-id})

          success-get-prop-body (:body success-get-running-prop-resp)
          current-prop (:current-prop success-get-prop-body)

          fail-end-event-resp (admin-end-event {:auth-token auth-token
                                                :status     405
                                                :channel-id channel-id})

          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                             :result     "true"
                                             :channel-id channel-id})

          end-event-resp (admin-end-event {:auth-token auth-token
                                           :channel-id channel-id})
          get-after-end-resp (get-event {:status 204
                                         :channel-id channel-id})]

      (is (string? (:proposition/start-time current-prop)))
      (is (string? (:proposition/betting-end-time current-prop)))
      (is (= #:proposition{:running?             true
                           :betting-seconds-left 29
                           :text                 text}
             (dissoc current-prop :proposition/start-time :proposition/betting-end-time)))

      (is (string? (:event/start-time get-response-body)))
      (is (= #:event{:auto-run      "event.auto-run/off"
                     :running?      true
                     :title         title
                     :stream-source "event.stream-source/twitch"
                     :channel-id    "drdisrespect"}
             (dissoc get-response-body :event/start-time))))))

;; TODO: test user cash and notifications
;; TODO: test suggestions
(deftest events-in-parallel-happy-path
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        {user-auth-token :auth-token} (create-user-and-login dummy-user-2)
        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        title2 "yert"
        channel-id2 "donkeykong"
        resp (admin-create-event {:auth-token auth-token
                                  :title      title
                                  :channel-id channel-id})
        fail-create-again-resp (admin-create-event {:auth-token auth-token
                                                    :title      title2
                                                    :channel-id channel-id
                                                    :status     405})
        resp2 (admin-create-event {:auth-token auth-token
                                   :title      title2
                                   :channel-id channel-id2})

        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       "prop1"
                                                 :channel-id channel-id})
        create-prop-bet-resp2 (admin-create-prop {:auth-token auth-token
                                                 :text       "prop2"
                                                 :channel-id channel-id2})

        admin-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       100
                                                       :channel-id channel-id})
        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       user-auth-token
                                                       :projected-result false
                                                       :bet-amount       150
                                                       :channel-id channel-id})
        admin-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result false
                                                       :bet-amount       100
                                                       :channel-id channel-id2})
        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       user-auth-token
                                                        :projected-result true
                                                        :bet-amount      150
                                                        :channel-id channel-id2})

        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})
        current-prop-bets-response2 (get-prop-bets-leaderboard {:channel-id channel-id2})
        event-score-before-prop-result (get-event-leaderboard {:channel-id channel-id})
        event-score-before-prop-result2 (get-event-leaderboard {:channel-id channel-id2})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})
        end-prop-bet-resp2 (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id2})

        event-score-after-prop-result (get-event-leaderboard {:channel-id channel-id})
        event-score-after-prop-result2 (get-event-leaderboard {:channel-id channel-id2})

        get-all-events-resp (get-events)
        end (admin-end-event {:auth-token auth-token
                              :channel-id channel-id2})

        get-all-events-resp2 (get-events)

        end2 (admin-end-event {:auth-token auth-token
                               :channel-id channel-id})

        get-all-events-resp3 (get-events {:status 204})]

    (is (= {:false {:bets  [{:bet/amount 150
                             :user/name  "donniedarko"}]
                    :odds  1.666666666666667
                    :total 150}
            :true  {:bets  [{:bet/amount 100
                             :user/name  "queefburglar"}]
                    :odds  2.5
                    :total 100}}
           (:body current-prop-bets-response)))

    (is (= {:false {:bets  [{:bet/amount 100
                             :user/name  "queefburglar"}]
                    :odds  2.5
                    :total 100}
            :true  {:bets  [{:bet/amount 150
                             :user/name  "donniedarko"}]
                    :odds  1.666666666666667
                    :total 150}}
           (:body current-prop-bets-response2)))

    (is (= '({:score     0
             :user_name "donniedarko"}
            {:score     0
             :user_name "queefburglar"})
           (sort-by :user_name (:body event-score-before-prop-result))
           (sort-by :user_name (:body event-score-before-prop-result2))))

    (is (= [{:score     160
             :user_name "queefburglar"}
            {:score     -150
             :user_name "donniedarko"}]
           (:body event-score-after-prop-result)))

    (is (= [{:score     111
             :user_name "donniedarko"}
            {:score     -100
             :user_name "queefburglar"}]
           (:body event-score-after-prop-result2)))

    (is (= '(#:event{:auto-run      "event.auto-run/off"
                     :channel-id    "donkeykong"
                     :running?      true
                     :stream-source "event.stream-source/twitch"
                     :title         "yert"}
              #:event{:auto-run      "event.auto-run/off"
                      :channel-id   "drdisrespect"
                      :running?      true
                      :stream-source "event.stream-source/twitch"
                      :title         "Dirty Dan's Delirious Dance Party"})
           (->> get-all-events-resp
                :body
                (sort-by :event/channel-id)
                (map #(dissoc % :event/start-time)))))

    (is (= '(#:event{:auto-run      "event.auto-run/off"
                     :channel-id    "drdisrespect"
                     :running?      true
                     :stream-source "event.stream-source/twitch"
                     :title         "Dirty Dan's Delirious Dance Party"})
           (->> get-all-events-resp2
                :body
                (sort-by :event/start-time)
                (map #(dissoc % :event/start-time)))))))

(deftest fail-create-empty-prop
  (testing "successfully create and get event with proper admin role"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          title "Dirty Dan's Delirious Dance Party"
          channel-id "drdisrespect"
          resp (admin-create-event {:auth-token auth-token
                                    :title      title
                                    :channel-id channel-id})
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text       ""
                                                   :channel-id channel-id
                                                   :status     405})])))

(deftest success-get-event
  (testing "successfully get nonexistent event, don't need admin"
    (get-event {:status 204
                :channel-id "foo"})))

(deftest fail-end-event
  (testing "fails because user is not an admin"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login)]
      (admin-end-event {:auth-token auth-token
                        :status     403
                        :channel-id "foo"}))))

(deftest no-event-to-end
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))]
    (admin-end-event {:auth-token auth-token
                      :status     405
                      :channel-id "foo"})))

(deftest admin-and-user-create-prop-bet
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        event-score-before-event-creation (get-event-leaderboard {:status 204
                                                                  :channel-id channel-id})

        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        event-score-before-prop-creation (get-event-leaderboard {:status 204
                                                                 :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        ;; have admin bet for notifications
        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result false
                                :bet-amount       500
                                :channel-id channel-id})

        _ (create-user dummy-user-2)
        _ (create-user dummy-user-3)

        ;; user 2 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-2)

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       100
                                                       :channel-id channel-id})

        user-place-prop-bet-resp-a (user-place-prop-bet {:auth-token       auth-token
                                                         :projected-result true
                                                         :bet-amount       100
                                                         :channel-id channel-id})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       300
                                                        :channel-id channel-id})

        ;; user 3 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-3)

        user-place-prop-bet-resp3 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       100
                                                        :channel-id channel-id})
        user-place-prop-bet-resp4 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       400
                                                        :channel-id channel-id})

        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})

        event-score-before-prop-result (get-event-leaderboard {:channel-id channel-id})
        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})

        event-score-before-end (get-event-leaderboard {:channel-id channel-id})

        admin-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-3)
        user3-get-user (get-user {:auth-token auth-token})

        ;;admin end event
        {:keys [auth-token] login-resp :response} (login)
        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id channel-id})

        ;; moved get user calls lower because the notifcations acknowledgement happens
        ;; async and wouldnt always finish in time for the next get-user call
        admin-get-user-notifs-acked (get-user {:auth-token auth-token})
        user2-get-user-notifs-acked (get-user {:auth-token auth-token})
        user3-get-user-notifs-acked (get-user {:auth-token auth-token})
        event-score-after-end (get-event-leaderboard {:channel-id channel-id})]

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

    (is (= nil
           (:body event-score-before-event-creation)
           (:body event-score-before-prop-creation)))

    (is (= [{:score     0
             :user_name "donniedarko"}
            {:score     0
             :user_name "kittycuddler420"}
            {:score     0
             :user_name "queefburglar"}]
           (->> event-score-before-prop-result
                :body
                (sort-by :user_name)
                vec)))

    (is (= [{:score     520
             :user_name "kittycuddler420"}
            {:score     20
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

    (is (= 520 (-> user2-get-user :body :user/cash)))
    ;; 2 payouts for the same proposition coalesced into one notification
    (is (= [{:bet/payout         520
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/true"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> user2-get-user :body :user/notifications)))
    (is (= []
           (-> user2-get-user-notifs-acked :body :user/notifications)))

    (is (= 1020 (-> user3-get-user :body :user/cash)))
    (is (= [{:bet/payout         1020
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/true"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> user3-get-user :body :user/notifications)))
    (is (= []
           (-> user3-get-user-notifs-acked :body :user/notifications)))))

;;TODO test for no previous outcome to flip
;;TODO test for previous prop cancelled, so cant flip it
(deftest flip-proposition-outcome
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        ;; have admin bet for notifications
        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result false
                                :bet-amount       400
                                :channel-id channel-id})

        _ (create-user dummy-user-2)
        _ (create-user dummy-user-3)

        ;; user 2 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-2)

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       100
                                                       :channel-id channel-id})

        user-place-prop-bet-respa (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       100
                                                        :channel-id channel-id})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       300
                                                        :channel-id channel-id})

        ;; user 3 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-3)

        user-place-prop-bet-resp3 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       100
                                                        :channel-id channel-id})
        user-place-prop-bet-resp4 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       400
                                                        :channel-id channel-id})

        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})

        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})

        event-score-before-flip (get-event-leaderboard {:channel-id channel-id})

        {:keys [auth-token] login-resp :response} (login)
        ;; flip proposition outcome
        flip-outcome-resp (admin-flip-prop-outcome {:auth-token auth-token
                                                    :channel-id channel-id})
        event-score-after-flip (get-event-leaderboard {:channel-id channel-id})

        admin-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-3)
        user3-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login)
        ;;admin end event
        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id channel-id})]

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

    (is (= [{:score     454
             :user_name "kittycuddler420"}
            {:score     -13
             :user_name "donniedarko"}
            {:score     -400
             :user_name "queefburglar"}]
           (:body event-score-before-flip)))

    (is (= [{:score     330
             :user_name "queefburglar"}
            {:score     55
             :user_name "donniedarko"}
            {:score     -295
             :user_name "kittycuddler420"}]
           (:body event-score-after-flip)))

    ;; notifications
    (is (= 830 (-> admin-get-user :body :user/cash)))
    (is (= [{:bet/payout         730
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/false"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> admin-get-user :body :user/notifications)))

    (is (= 555 (-> user2-get-user :body :user/cash)))
    (is (= [;; First notif wasn't shown, and prop flipped, so it comes up as 0 here
            {:bet/payout         0
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/false"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}
            {:bet/payout         555
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/false"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> user2-get-user :body :user/notifications)))

    (is (= 205 (-> user3-get-user :body :user/cash)))
    (is (= [;; First notif wasn't shown, and prop flipped, so it comes up as 0 here
            {:bet/payout         0
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/false"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}
            {:bet/payout         205
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/false"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> user3-get-user :body :user/notifications)))))

(deftest flip-prop-outcome-test-bail
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        ;; have admin bet for notifications
        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result true
                                :bet-amount       500
                                :channel-id channel-id})

        _ (create-user dummy-user-2)

        ;; user 2 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-2)

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result false
                                                       :bet-amount       200
                                                       :channel-id channel-id})

        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})

        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user-before-flip (get-user {:auth-token auth-token})

        event-score-before-flip (get-event-leaderboard {:channel-id channel-id})

        {:keys [auth-token] login-resp :response} (login)
        admin-get-user-before-flip (get-user {:auth-token auth-token})
        ;; flip proposition outcome
        flip-outcome-resp (admin-flip-prop-outcome {:auth-token auth-token
                                                    :channel-id channel-id})
        event-score-after-flip (get-event-leaderboard {:channel-id channel-id})

        admin-get-user-after-flip (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user-after-flip (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login)
        ;;admin end event
        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id channel-id})]

    (is (= {:false {:bets  [{:bet/amount 200
                             :user/name  "donniedarko"}]
                    :odds  3.5
                    :total 200}
            :true  {:bets  [{:bet/amount 500
                             :user/name  "queefburglar"}]
                    :odds  1.4
                    :total 500}}
           (:body current-prop-bets-response)))

    (is (= [{:score     210
             :user_name "queefburglar"}
            {:score     -200
             :user_name "donniedarko"}]
           (:body event-score-before-flip)))

    (is (= [{:score     510
             :user_name "donniedarko"}
            {:score     -500
             :user_name "queefburglar"}]
           (:body event-score-after-flip)))

    ;; notifications
    (is (= 710 (-> admin-get-user-before-flip :body :user/cash)))
    (is (= [{:bet/payout         710
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/true"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> admin-get-user-before-flip :body :user/notifications)))

    (is (= 100 (-> admin-get-user-after-flip :body :user/cash)))
    (is (= [#:notification{:type "notification.type/bailout"}]
           (-> admin-get-user-after-flip :body :user/notifications)))

    (is (= 300 (-> user2-get-user-before-flip :body :user/cash)))
    (is (= []
           (-> user2-get-user-before-flip :body :user/notifications)))

    (is (= 1010 (-> user2-get-user-after-flip :body :user/cash)))
    (is (= [{:bet/payout         710
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/false"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> user2-get-user-after-flip :body :user/notifications)))))

(deftest cancel-proposition
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        ;; have admin bet for notifications
        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result false
                                :bet-amount       400
                                :channel-id channel-id})

        _ (create-user dummy-user-2)
        _ (create-user dummy-user-3)

        ;; user 2 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-2)

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       100
                                                       :channel-id channel-id})

        user-place-prop-bet-respa (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       100
                                                        :channel-id channel-id})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       300
                                                        :channel-id channel-id})

        ;; user 3 bets
        {:keys [auth-token] login-resp :response} (login dummy-user-3)

        user-place-prop-bet-resp3 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result false
                                                        :bet-amount       100
                                                        :channel-id channel-id})
        user-place-prop-bet-resp4 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       400
                                                        :channel-id channel-id})

        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})

        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "cancel"
                                           :channel-id channel-id})

        event-score-before-cancel (get-event-leaderboard {:channel-id channel-id})

        {:keys [auth-token] login-resp :response} (login)
        flip-outcome-resp (admin-flip-prop-outcome {:auth-token auth-token
                                                    :channel-id channel-id
                                                    :status     405})
        event-score-after-cancel (get-event-leaderboard {:channel-id channel-id})

        admin-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-2)
        user2-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login dummy-user-3)
        user3-get-user (get-user {:auth-token auth-token})

        {:keys [auth-token] login-resp :response} (login)
        ;;admin end event
        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id channel-id})]

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

    (is (every? (fn [entry]
                  (= 0 (:score entry)))
                (:body event-score-before-cancel)))

    (is (every? (fn [entry]
                  (= 0 (:score entry)))
                (:body event-score-after-cancel)))

    ;; notifications
    (is (= 500 (-> admin-get-user :body :user/cash)))
    (is (= [{:bet/payout         400
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/cancelled"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> admin-get-user :body :user/notifications)))

    (is (= 500 (-> user2-get-user :body :user/cash)))
    (is (= [{:bet/payout         200
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/cancelled"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}
            {:bet/payout         300
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/cancelled"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> user2-get-user :body :user/notifications)))

    (is (= 500 (-> user3-get-user :body :user/cash)))
    (is (= [{:bet/payout         400
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/cancelled"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}
            {:bet/payout         100
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/cancelled"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (sort-by :bet/payout #(compare %2 %1) (-> user3-get-user :body :user/notifications))))))

(deftest no-payout-doesnt-break
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        end-betting-secs 30
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id
                                                 :end-betting-secs end-betting-secs})

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       500
                                                       :channel-id channel-id})

        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})
        now (time/now)
        future-prop-bets-response (with-redefs [whiplash.time/now (fn []
                                                                    (time/seconds-delta now end-betting-secs))]
                                    (get-prop-bets-leaderboard {:channel-id channel-id}))


        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

        ;;admin bet and reset to 100
        _ (is (= 100 (-> (get-user {:auth-token auth-token}) :body :user/cash)))

        ;;admin end event
        {:keys [auth-token] login-resp :response} (login)
        resp (admin-end-event {:auth-token auth-token
                               :channel-id channel-id})]

    (is (= {:true  {:bets  [{:bet/amount 500
                             :user/name  "queefburglar"}]
                    :odds  1.0
                    :total 500}
            :false {:bets  []
                    :odds  500.0
                    :total 0}}
           (:body current-prop-bets-response)))
    ;; Odds and total are faked when people bet only on one side after betting has concluded
    (is (= {:true  {:bets  [{:bet/amount 500
                             :user/name  "queefburglar"}]
                    :odds  2.00
                    :total 500}
            :false {:bets  []
                    :odds  1.0
                    :total 1000}}
           (:body future-prop-bets-response)))))

(deftest bet-both-sides-doesnt-break
  (testing ""
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))

          title "Dirty Dan's Delirious Dance Party"
          channel-id "drdisrespect"
          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title      title
                                                 :channel-id channel-id})

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text       text
                                                   :channel-id channel-id})

          user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                         :projected-result true
                                                         :bet-amount       300
                                                         :channel-id channel-id})

          user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                          :projected-result false
                                                          :bet-amount       200
                                                          :channel-id channel-id})

          current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})

          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

          _ (is (= 510 (-> (get-user {:auth-token auth-token}) :body :user/cash)))

          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          end-event-resp (admin-end-event {:auth-token auth-token
                                           :channel-id channel-id})

          event-score-resp (get-event-leaderboard {:channel-id channel-id})]

      (is (= {:false {:bets  [{:bet/amount 200
                               :user/name  "queefburglar"}]
                      :odds  2.5
                      :total 200}
              :true  {:bets  [{:bet/amount 300
                               :user/name  "queefburglar"}]
                      :odds  1.666666666666667
                      :total 300}}
             (:body current-prop-bets-response)))

      (is (= [{:score     10
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
                               :text       "foo"
                               :status     405}))))

(deftest user-cant-submit-suggestion-text
  (testing "invalid text"
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          channel-id "donnie"]
      (admin-create-event {:auth-token auth-token
                           :title      "hi"
                           :channel-id channel-id})
      (user-submit-suggestion {:auth-token auth-token
                               :text       ""
                               :channel-id channel-id
                               :status     405})
      (user-submit-suggestion {:auth-token auth-token
                               :text       "this string is going to be very long and possibly even over 100 characters wowee zowie. I don't know."
                               :channel-id channel-id
                               :status     405}))))

(deftest user-cant-suggest-email-not-verified
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        channel-id "donnie"
        _ (admin-create-event {:auth-token auth-token
                               :title      "hi"
                               :channel-id channel-id})

        _ (create-user (assoc dummy-user-2 :verify? false))

        {:keys [auth-token] login-resp :response} (login dummy-user-2)]

    (user-submit-suggestion {:auth-token auth-token
                             :text       "Hello this is a valid suggestion."
                             :channel-id channel-id
                             :status     405})))

(deftest dismiss-suggestions-success
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        channel-id "donnie"
        _ (admin-create-event {:auth-token auth-token
                               :title      "hi"
                               :channel-id channel-id})
        _ (user-submit-suggestion {:auth-token auth-token
                                   :text       "Captain Falcon gets 2 or more dunks this round."
                                   :channel-id channel-id})
        _ (user-submit-suggestion {:auth-token auth-token
                                   :text       "Dirty Dan dunks Donnie 17 or more times this round."
                                   :channel-id channel-id})
        dismiss-text "This suggestion is going to get dismissed by an admin."

        _ (dotimes [_ 2]
            (user-submit-suggestion {:auth-token auth-token
                                     :text       dismiss-text
                                     :channel-id channel-id}))

        get-suggestions-resp (admin-get-suggestions {:auth-token auth-token
                                                     :channel-id channel-id})
        suggestions-to-dismiss (->> get-suggestions-resp
                                    :body
                                    (filter #(= (:suggestion/text %) dismiss-text))
                                    (mapv :suggestion/uuid))

        _ (admin-dismiss-suggestions {:auth-token  auth-token
                                      :channel-id channel-id
                                      :suggestions suggestions-to-dismiss})

        ;; These UUIDs don't correspond to any suggestions
        _ (admin-dismiss-suggestions {:auth-token  auth-token
                                      :suggestions [(str (uuid/v4))]
                                      :channel-id channel-id
                                      :status      404})
        get-suggestions-after-dismiss-resp (admin-get-suggestions {:auth-token auth-token
                                                                   :channel-id channel-id})]

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
        channel-id "donnie"
        _ (admin-create-event {:auth-token auth-token
                               :title      "hi"
                               :channel-id channel-id})
        get-suggestions (admin-get-suggestions {:auth-token auth-token
                                                :status     204
                                                :channel-id channel-id})]
    (is (nil? (:body get-suggestions)))))

(deftest admin-get-empty-suggestion-no-event
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        get-suggestions (admin-get-suggestions {:auth-token auth-token
                                                :status     204})]
    (is (nil? (:body get-suggestions)))))

(deftest end-betting-for-proposition
  (let [{:keys [auth-token]} (create-user-and-login
                               (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})
        text "Will Jon wipeout 2+ times this round?"
        end-betting-secs 30
        create-prop-bet-resp (admin-create-prop {:auth-token       auth-token
                                                 :channel-id channel-id
                                                 :text             text
                                                 :end-betting-secs end-betting-secs})
        now (time/now)]
    (user-place-prop-bet {:auth-token       auth-token
                          :projected-result true
                          :bet-amount       300
                          :channel-id channel-id})
    (is (string? (-> (get-prop {:channel-id channel-id}) :body :current-prop :proposition/betting-end-time)))
    ;; TODO: change tests so we can move time forward more sanely
    ;; User cannot place a bet after the betting window is over
    (with-redefs [whiplash.time/now (fn []
                                      (time/seconds-delta now end-betting-secs))]
      (user-place-prop-bet {:auth-token       auth-token
                            :projected-result true
                            :bet-amount       200
                            :status           405
                            :channel-id channel-id}))
    (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})
    (admin-end-event {:auth-token auth-token
                      :result     true
                      :channel-id channel-id})))

(deftest get-previous-prop
  (let [{:keys [auth-token]} (create-user-and-login
                               (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})
        text "Will Jon wipeout 2+ times this round?"
        end-betting-secs 30
        create-prop-bet-resp (admin-create-prop {:auth-token       auth-token
                                                 :text             text
                                                 :channel-id channel-id
                                                 :end-betting-secs end-betting-secs})]

    ;; asserts about current prop is good and previous prop is empty
    (is (empty? (-> (get-prop {:channel-id channel-id}) :body :previous-prop)))
    (is (= text
           (-> (get-prop {:channel-id channel-id}) :body :current-prop :proposition/text)))

    (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})
    (admin-create-prop {:auth-token       auth-token
                        :text             "foo"
                        :channel-id channel-id
                        :end-betting-secs end-betting-secs})

    ;; asserts about current prop and previous prop
    (is (= text
           (-> (get-prop {:channel-id channel-id}) :body :previous-prop :proposition/text)))
    (is (= "proposition.result/true"
           (-> (get-prop {:channel-id channel-id}) :body :previous-prop :proposition/result)))
    (is (= "foo"
           (-> (get-prop {:channel-id channel-id}) :body :current-prop :proposition/text)))

    (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

    ;;asserts about prev prop but no current prop
    (is (= "foo"
           (-> (get-prop {:channel-id channel-id}) :body :previous-prop :proposition/text)))
    (is (= "proposition.result/false"
           (-> (get-prop {:channel-id channel-id}) :body :previous-prop :proposition/result)))
    (is (empty? (-> (get-prop {:channel-id channel-id}) :body :current-prop)))

    (admin-end-event {:auth-token auth-token
                      :result     true
                      :channel-id channel-id})))

(deftest create-youtube-live-event
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dirty Dancing Watch Party"
        youtube-channel-id "x135bZ6G"
        resp (admin-create-event {:auth-token auth-token
                                  :title      title
                                  :channel-id youtube-channel-id
                                  :source     "youtube"})
        fail-create-again-resp (admin-create-event {:auth-token auth-token
                                                    :title      title
                                                    :channel-id youtube-channel-id
                                                    :source     "youtube"
                                                    :status     405})

        get-event-response (get-event {:channel-id youtube-channel-id})
        get-response-body (:body get-event-response)

        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id youtube-channel-id})
        get-after-end-resp (get-event {:status 204
                                       :channel-id youtube-channel-id})]

    (is (string? (:event/start-time get-response-body)))
    ;; TODO: revisit. probably need to reimplement youtube form the ground up
    ;; channel id is now lowercased by everything which may break the youtube integration
    #_(is (= #:event{:running?      true
                   :title         title
                   :stream-source "event.stream-source/youtube"
                   :channel-id    youtube-channel-id}
           (dissoc get-response-body :event/start-time)))))

(deftest create-none-stream-source-event
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dirty Dancing Watch Party"
        channel-id "not_used"
        resp (admin-create-event {:auth-token auth-token
                                  :title      title
                                  :channel-id channel-id
                                  :source     "none"})
        fail-create-again-resp (admin-create-event {:auth-token auth-token
                                                    :title      title
                                                    :channel-id channel-id
                                                    :source     "none"
                                                    :status     405})

        get-event-response (get-event {:channel-id channel-id})
        get-response-body (:body get-event-response)

        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id channel-id})
        get-after-end-resp (get-event {:status 204
                          :channel-id channel-id})]

    (is (string? (:event/start-time get-response-body)))
    (is (= #:event{:auto-run      "event.auto-run/off"
                   :running?      true
                   :title         title
                   :stream-source "event.stream-source/none"
                   :channel-id   channel-id}
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
                                    :source     "blarp"
                                    :status     400})])))

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
        cnn-channel-id "doesnt_matter_what_this_is"
        resp (admin-create-event {:auth-token auth-token
                                  :title      title
                                  :channel-id cnn-channel-id
                                  :source     "cnn-unauth"})
        fail-create-again-resp (admin-create-event {:auth-token auth-token
                                                    :title      title
                                                    :channel-id cnn-channel-id
                                                    :source     "cnn-unauth"
                                                    :status     405})

        get-event-response (get-event {:channel-id cnn-channel-id})
        get-response-body (:body get-event-response)

        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id cnn-channel-id})
        get-after-end-resp (get-event {:status 204
                          :channel-id cnn-channel-id})]

    (is (string? (:event/start-time get-response-body)))
    (is (= #:event{:auto-run      "event.auto-run/off"
                   :running?      true
                   :title         title
                   :stream-source "event.stream-source/cnn-unauth"
                   :channel-id    cnn-channel-id}
           (dissoc get-response-body :event/start-time)))))

(deftest prop-bets-leaderboard-empty
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})
        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})
        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})]
    (is (= {:false {:bets  []
                    :odds  1.0
                    :total 0}
            :true  {:bets  []
                    :odds  1.0
                    :total 0}}
           (:body current-prop-bets-response)))))

(deftest multiple-propositions-correct-score
  (testing ""
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))

          title "Dirty Dan's Delirious Dance Party"
          channel-id "drdisrespect"
          ;; Create and end to test event score leaderboard at end
          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title      title
                                                 :channel-id channel-id})

          end-event-resp (admin-end-event {:auth-token auth-token
                                           :channel-id channel-id})

          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title      title
                                                 :channel-id channel-id})

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text       text
                                                   :channel-id channel-id})

          _ (create-user dummy-user-2)
          _ (create-user dummy-user-3)

          ;; user 2 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-2)

          user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                         :projected-result true
                                                         :bet-amount       200
                                                         :channel-id channel-id})

          user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                          :projected-result false
                                                          :bet-amount       300
                                                          :channel-id channel-id})

          ;; user 3 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-3)

          user-place-prop-bet-resp3 (user-place-prop-bet {:auth-token       auth-token
                                                          :projected-result false
                                                          :bet-amount       100
                                                          :channel-id channel-id})
          user-place-prop-bet-resp4 (user-place-prop-bet {:auth-token       auth-token
                                                          :projected-result true
                                                          :bet-amount       400
                                                          :channel-id channel-id})

          current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})

          event-score-before-prop-result (get-event-leaderboard {:channel-id channel-id})
          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})
          all-time-leaderboard-first-prop ((common/test-app) (-> (mock/request :get "/leaderboard/all-time")))

          event-score-first-prop (get-event-leaderboard {:channel-id channel-id})

          create-second-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                          :text       "zoinks"
                                                          :channel-id channel-id})

          ;; user 2 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-2)

          user-place-second-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                                :projected-result true
                                                                :bet-amount       100
                                                                :channel-id channel-id})

          ;; user 3 bets
          {:keys [auth-token] login-resp :response} (login dummy-user-3)

          user-place-second-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                                 :projected-result false
                                                                 :bet-amount       120
                                                                 :channel-id channel-id})

          prop-bets-second-response (get-prop-bets-leaderboard {:channel-id channel-id})
          user3-get-user (get-user {:auth-token auth-token})

          ;; login as admin
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

          event-score-second-prop (get-event-leaderboard {:channel-id channel-id})
          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          end-event-resp (admin-end-event {:auth-token auth-token
                                           :channel-id channel-id})
          event-score-after-end (get-event-leaderboard {:channel-id channel-id})

          {:keys [auth-token] login-resp :response} (login dummy-user-3)
          user3-get-user-after-event (get-user {:auth-token auth-token})

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
             (->> event-score-before-prop-result
                  :body
                  (sort-by :user_name)
                  vec)))

      (is (= [{:score     187
               :user_name "kittycuddler420"}
              {:score     -146
               :user_name "donniedarko"}]
             (:body event-score-first-prop)))

      (is (= [{:cash      687
               :user_name "kittycuddler420"}
              {:cash      500
               :user_name "queefburglar"}
              ;; Users below 500 are filtered now
              #_{:cash      354
                 :user_name "donniedarko"}]
             (common/parse-json-body all-time-leaderboard-first-prop)))

      (is (= {:false {:bets  [{:bet/amount 120
                               :user/name  "kittycuddler420"}]
                      :odds  1.833333333333333
                      :total 120}
              :true  {:bets  [{:bet/amount 100
                               :user/name  "donniedarko"}]
                      :odds  2.2
                      :total 100}}
             (:body prop-bets-second-response)))

      (is (= [{:score     297
               :user_name "kittycuddler420"}
              {:score     -246
               :user_name "donniedarko"}]
             (:body event-score-second-prop)
             (:body event-score-after-end)))

      (is (= [{:cash      797
               :user_name "kittycuddler420"}
              {:cash      500
               :user_name "queefburglar"}
              ;; Users below 500 are filtered now
              #_{:cash      254
                 :user_name "donniedarko"}]
             (common/parse-json-body all-time-leaderboard-end)))

      (testing "end event doesnt change normal user balance"
        (= (-> user3-get-user :body :user/cash)
           (-> user3-get-user-after-event :body :user/cash))))))

(deftest end-proposition-no-bets
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})
        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id channel-id})]))

(deftest weird-notifications-behavior-test
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        bet1 (user-place-prop-bet {:auth-token       auth-token
                                   :projected-result true
                                   :bet-amount       500
                                   :channel-id channel-id})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       "second one"
                                                 :channel-id channel-id})

        bet2 (user-place-prop-bet {:auth-token       auth-token
                                   :projected-result true
                                   :bet-amount       100
                                   :channel-id channel-id})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})

        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       "third one"
                                                 :channel-id channel-id})

        bet4 (user-place-prop-bet {:auth-token       auth-token
                                   :projected-result true
                                   :bet-amount       210
                                   :channel-id channel-id})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})

        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       "fourth one"
                                                 :channel-id channel-id})

        bet6 (user-place-prop-bet {:auth-token       auth-token
                                   :projected-result true
                                   :bet-amount       420
                                   :channel-id channel-id})

        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

        ;; normally the get would happen on a regular interval between these requests
        get-user-resp (get-user {:auth-token auth-token})]

    (is (= [#:notification{:type "notification.type/bailout"}
            #:notification{:type "notification.type/bailout"}
            {:bet/payout         210
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/true"
             :proposition/text   "second one"}
            {:bet/payout         430
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/true"
             :proposition/text   "third one"}]
           (->> (-> get-user-resp :body :user/notifications)
                (sort-by :proposition/text)
                vec)))))

#_(deftest cant-create-next-event-ts-invalid-ts
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))]
      (admin-create-next-event-ts {:auth-token auth-token
                                   :status     400
                                   :ts         "foobar"})))

#_(deftest cant-create-next-event-ts-in-past
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))]
      (admin-create-next-event-ts {:auth-token auth-token
                                   :status     400
                                   :ts         "2000-04-01T22:56:01Z"})))

#_(deftest get-event-doesn't-return-old-next-event-ts
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          ts "2100-04-01T22:56:01Z"
          now (time/now)]
      (admin-create-next-event-ts {:auth-token auth-token :ts ts})

      (is (= {:whiplash/next-event-time ts}
             (:body (get-event {:channel-id channel-id}))))

      ;; TODO: change tests so we can move time forward more sanely
      (with-redefs [whiplash.time/now (fn []
                                        (time/days-delta now (* 365 100)))]
        (get-event {:status 204
                          :channel-id channel-id}))))

#_(deftest create-next-event-ts
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          ts "2100-04-01T22:56:01Z"
          ts-2 "2200-04-01T22:56:01Z"]
      (admin-create-next-event-ts {:auth-token auth-token :ts ts})

      (is (= {:whiplash/next-event-time ts}
             (:body (get-event {:channel-id channel-id}))))

      ;;implicitly tests that we're overwriting the original attribute, and not making a new entity
      (admin-create-next-event-ts {:auth-token auth-token :ts ts-2})
      (is (= {:whiplash/next-event-time ts-2}
             (:body (get-event {:channel-id channel-id}))))))

#_(deftest get-event-returns-event-over-ts
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))
          ts "2100-04-01T22:56:01Z"]
      (admin-create-next-event-ts {:auth-token auth-token :ts ts})

      (is (= {:whiplash/next-event-time ts}
             (:body (get-event {:channel-id channel-id}))))

      (admin-create-event {:auth-token auth-token
                           :title      "poops"
                           :channel-id "pig boops"})

      (is (not= {:whiplash/next-event-time ts}
                (:body (get-event {:channel-id channel-id}))))))

(deftest recover-account-with-username
  (let [create-resp (create-user dummy-user)
        {:keys [recovery/token] :as request-recovery-resp} (request-password-recovery {:email-count 2})
        new-password "big_ole_bears"
        submit-password-recovery-resp (submit-password-recovery {:token    token
                                                                 :password new-password})
        login-resp (login {:user_name (:user_name dummy-user)
                           :password  new-password})
        request-recovery-resp2 (request-password-recovery {:email-count 3})
        submit-password-recovery-resp (submit-password-recovery {:token    (:recovery/token request-recovery-resp2)
                                                                 :password new-password})
        login-resp2 (login {:user_name (:user_name dummy-user)
                            :password  new-password})]
    ;; new token is issued after the old one is used
    (is (not= token (:recovery/token request-recovery-resp2)))))

(deftest recover-account-with-email
  (let [create-resp (create-user dummy-user)
        {:keys [recovery/token] :as request-recovery-resp} (request-password-recovery {:email-count 2
                                                                                       :method      :email})
        new-password "big_ole_bears"
        submit-password-recovery-resp (submit-password-recovery {:token    token
                                                                 :password new-password})
        login-resp (login {:user_name (:email dummy-user)
                           :password  new-password})]))

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
        submit-password-recovery-resp (submit-password-recovery {:email    "does-not-exist"
                                                                 :token    token
                                                                 :password new-password
                                                                 :status   404})]
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
        submit-password-recovery-resp (submit-password-recovery {:token    token
                                                                 :status   409
                                                                 :password new-password})]
    (is (= 401
           (:status
             ;; TODO: refactor login so we can pass status
             ((common/test-app) (-> (mock/request :post "/user/login")
                                    (mock/json-body {:user_name (:user_name dummy-user)
                                                     :password  new-password}))))))))

(deftest valid-auth-or-twitch-opaque-cookie?-middleware-test
  (user-place-prop-bet {:auth-token       nil
                        :projected-result false
                        :twitch-id?       false
                        #_#_:ga-tag?          false
                        :channel-id "hi"
                        :bet-amount       500
                        :status           403})
  (get-user {:auth-token nil
             :twitch-id?       false
             #_#_:ga-tag?    false
             :status     403}))

#_(deftest not-logged-in-user-no-bailout
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))

          event-score-before-event-creation (get-event-leaderboard {:status 204
                                                        :channel-id channel-id})

          title "Dirty Dan's Delirious Dance Party"
          channel-id "drdisrespect"
          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title      title
                                                 :channel-id channel-id})

          event-score-before-prop-creation (get-event-leaderboard {:status 204
                                                        :channel-id channel-id})

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text       text
                                                   :channel-id channel-id})

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

          current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})
          event-score-before-prop-result (get-event-leaderboard {:channel-id channel-id})

          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

          event-score-before-end (get-event-leaderboard {:channel-id channel-id})

          admin-get-user (get-user {:auth-token auth-token})
          admin-get-user-notifs-acked (get-user {:auth-token auth-token})

          user2-get-user (get-user nil)
          user2-get-user-notifs-acked (get-user nil)

          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          end-event-resp (admin-end-event {:auth-token auth-token
                                           :channel-id channel-id})

          event-score-after-end (get-event-leaderboard {:channel-id channel-id})
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

      (is (= nil
             (:body event-score-before-event-creation)
             (:body event-score-before-prop-creation)))

      (testing "unauth user does not appear on current event leaderboard"
        (is (= [{:score     0
                 :user_name "queefburglar"}]
               (:body event-score-before-prop-result))))

      (is (= [{:score     510
               :user_name "queefburglar"}]
             (:body event-score-before-end)
             (:body event-score-after-end)))

      (testing "unauth user does not appear in all time leaderboard"
        (is (= [{:cash      1010
                 :user_name "queefburglar"}]
               (:body all-time-leaderboard))))

      ;; notifications
      (is (= 1010 (-> admin-get-user :body :user/cash)))
      (is (= [{:bet/payout         1010
               :notification/type  "notification.type/payout"
               :proposition/result "proposition.result/false"
               :proposition/text   "Will Jon wipeout 2+ times this round?"}]
             (-> admin-get-user :body :user/notifications)))
      (is (= []
             (-> admin-get-user-notifs-acked :body :user/notifications)))

      (is (= 0 (-> user2-get-user :body :user/cash)))
      (is (= [#:notification{:type "notification.type/no-bailout"}]
             (-> user2-get-user :body :user/notifications)))
      (is (= []
             (-> user2-get-user-notifs-acked :body :user/notifications)))))

#_(deftest not-logged-in-user-no-leaderboard
    (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                      (assoc dummy-user :admin? true))

          event-score-before-event-creation (get-event-leaderboard {:status 204
                                                        :channel-id channel-id})

          title "Dirty Dan's Delirious Dance Party"
          channel-id "drdisrespect"
          create-event-resp (admin-create-event {:auth-token auth-token
                                                 :title      title
                                                 :channel-id channel-id})

          event-score-before-prop-creation (get-event-leaderboard {:status 204
                                                        :channel-id channel-id})

          text "Will Jon wipeout 2+ times this round?"
          create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                   :text       text
                                                   :channel-id channel-id})

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

          current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})
          event-score-before-prop-result (get-event-leaderboard {:channel-id channel-id})

          ;;admin end prop bet
          {:keys [auth-token] login-resp :response} (login)
          end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "true"
                                           :channel-id channel-id})

          event-score-before-end (get-event-leaderboard {:channel-id channel-id})

          admin-get-user (get-user {:auth-token auth-token})
          admin-get-user-notifs-acked (get-user {:auth-token auth-token})

          user2-get-user (get-user nil)
          user2-get-user-notifs-acked (get-user nil)

          ;;admin end event
          {:keys [auth-token] login-resp :response} (login)
          end-event-resp (admin-end-event {:auth-token auth-token
                                           :channel-id channel-id})

          event-score-after-end (get-event-leaderboard {:channel-id channel-id})

          user2-get-user-after-end (get-user nil)
          admin-get-user-after-end (get-user {:auth-token auth-token})
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

      (is (= nil
             (:body event-score-before-event-creation)
             (:body event-score-before-prop-creation)))

      (testing "unauth user does not appear on current event leaderboard"
        (is (= [{:score     0
                 :user_name "queefburglar"}]
               (:body event-score-before-prop-result))))

      (is (= [{:score     -500
               :user_name "queefburglar"}]
             (:body event-score-before-end)
             (:body event-score-after-end)))

      (testing "unauth user does not appear in all time leaderboard"
        (is (= []
               (:body all-time-leaderboard))))

      ;; notifications
      (is (= 100 (-> admin-get-user :body :user/cash)
             (-> admin-get-user-after-end :body :user/cash)))
      (is (= [#:notification{:type "notification.type/bailout"}]
             (-> admin-get-user :body :user/notifications)))
      (is (= []
             (-> admin-get-user-notifs-acked :body :user/notifications)))

      (is (= 1010 (-> user2-get-user :body :user/cash)))
      (is (= [{:bet/payout         1010
               :notification/type  "notification.type/payout"
               :proposition/result "proposition.result/true"
               :proposition/text   "Will Jon wipeout 2+ times this round?"}]
             (-> user2-get-user :body :user/notifications)))
      (is (= []
             (-> user2-get-user-notifs-acked :body :user/notifications)))
      (testing "twitch ext user's cash is reset to 500"
        (is (= 500 (-> user2-get-user-after-end :body :user/cash))))))

(deftest twitch-not-logged-in-user-no-bailout
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        event-score-before-event-creation (get-event-leaderboard {:status 204
                                                                  :channel-id channel-id})

        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        event-score-before-prop-creation (get-event-leaderboard {:status 204
                                                                 :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result false
                                :bet-amount       500
                                :channel-id channel-id})

        ;; logged out user bets
        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       nil
                                                       :twitch-id?       true
                                                       :projected-result true
                                                       :bet-amount       100
                                                       :channel-id channel-id})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       nil
                                                        :twitch-id?       true
                                                        :projected-result true
                                                        :bet-amount       400
                                                        :channel-id channel-id})

        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})
        event-score-before-prop-result (get-event-leaderboard {:channel-id channel-id})

        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

        event-score-before-end (get-event-leaderboard {:channel-id channel-id})

        admin-get-user (get-user {:auth-token auth-token})
        user2-get-user (get-user {:auth-token nil :twitch-id? true})

        ;;admin end event
        {:keys [auth-token] login-resp :response} (login)
        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id channel-id})

        user2-get-user-after-end (get-user {:auth-token nil :twitch-id? true})
        admin-get-user-after-end (get-user {:auth-token auth-token})

        event-score-after-end (get-event-leaderboard {:channel-id channel-id})
        all-time-leaderboard (all-time-top-ten {})]

    (is (= {:false {:bets  [{:bet/amount 500
                             :user/name  "queefburglar"}]
                    :odds  2.0
                    :total 500}
            :true  {:bets  [{:bet/amount 500
                             :user/name  "user-UtestIDcAg"}]
                    :odds  2.0
                    :total 500}}
           (:body current-prop-bets-response)))

    (is (= nil
           (:body event-score-before-event-creation)
           (:body event-score-before-prop-creation)))

    (testing "twitch unauth user does not appear on current event leaderboard"
      (is (= [{:score     0
               :user_name "queefburglar"}]
             (:body event-score-before-prop-result))))

    (is (= [{:score     510
             :user_name "queefburglar"}]
           (:body event-score-before-end)
           (:body event-score-after-end)))

    (testing "twitch unauth user does not appear in all time leaderboard"
      (is (= [{:cash      1010
               :user_name "queefburglar"}]
             (:body all-time-leaderboard))))

    ;; notifications
    (is (= 1010 (-> admin-get-user :body :user/cash)
           (-> admin-get-user-after-end :body :user/cash)))
    (is (= [{:bet/payout         1010
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/false"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> admin-get-user :body :user/notifications)))
    (is (= []
           (-> admin-get-user-after-end :body :user/notifications)))

    (is (= 100 (-> user2-get-user :body :user/cash)))
    (is (= [#:notification{:type "notification.type/bailout"}]
           (-> user2-get-user :body :user/notifications)))
    (is (false? (-> user2-get-user :body :user/gated?)))
    (is (= []
           (-> user2-get-user-after-end :body :user/notifications)))

    ;; TODO: consider renabling this on a per event reset basis
    #_(testing "twitch ext user's cash is reset to 500"
      (is (= 500 (-> user2-get-user-after-end :body :user/cash))))))

(deftest twitch-not-logged-in-user-not-on-all-time
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))
        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        event-score-before-event-creation (get-event-leaderboard {:status 204
                                                        :channel-id channel-id})
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        event-score-before-prop-creation (get-event-leaderboard {:status 204
                                                        :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        _ (user-place-prop-bet {:auth-token       auth-token
                                :projected-result true
                                :bet-amount       500
                                :channel-id channel-id})

        ;; logged out user bets
        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       nil
                                                       :twitch-id?          true
                                                       :projected-result false
                                                       :bet-amount       100
                                                       :channel-id channel-id})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       nil
                                                        :twitch-id?          true
                                                        :projected-result false
                                                        :bet-amount       400
                                                        :channel-id channel-id})

        current-prop-bets-response (get-prop-bets-leaderboard {:channel-id channel-id})
        event-score-before-prop-result (get-event-leaderboard {:channel-id channel-id})

        ;;admin end prop bet
        {:keys [auth-token] login-resp :response} (login)
        end-prop-bet-resp (admin-end-prop {:auth-token auth-token
                                           :result     "false"
                                           :channel-id channel-id})

        event-score-before-end (get-event-leaderboard {:channel-id channel-id})

        admin-get-user (get-user {:auth-token auth-token})
        user2-get-user (get-user {:auth-token nil :twitch-id? true})

        ;;admin end event
        {:keys [auth-token] login-resp :response} (login)
        end-event-resp (admin-end-event {:auth-token auth-token
                                         :channel-id channel-id})

        admin-get-user-notifs-acked (get-user {:auth-token auth-token})
        user2-get-user-notifs-acked (get-user {:auth-token nil :twitch-id? true})
        event-score-after-end (get-event-leaderboard {:channel-id channel-id})
        all-time-leaderboard (all-time-top-ten {})]

    (is (= {:false {:bets  [{:bet/amount 500
                             :user/name  "user-UtestIDcAg"}]
                    :odds  2.0
                    :total 500}
            :true  {:bets  [{:bet/amount 500
                             :user/name  "queefburglar"}]
                    :odds  2.0
                    :total 500}}
           (:body current-prop-bets-response)))

    (is (= nil
           (:body event-score-before-event-creation)
           (:body event-score-before-prop-creation)))

    (testing "twitch unauth user does not appear on current event leaderboard"
      (is (= [{:score     0
               :user_name "queefburglar"}]
             (:body event-score-before-prop-result))))

    (is (= [{:score     -500
             :user_name "queefburglar"}]
           (:body event-score-before-end)
           (:body event-score-after-end)))

    (testing "twitch unauth user does not appear in all time leaderboard, other user doesnt appear because cash below 500"
      (is (= []
             (:body all-time-leaderboard))))

    ;; notifications
    (is (= 100 (-> admin-get-user :body :user/cash)))
    (is (= [#:notification{:type "notification.type/bailout"}]
           (-> admin-get-user :body :user/notifications)))
    (is (= []
           (-> admin-get-user-notifs-acked :body :user/notifications)))

    (is (= 1010 (-> user2-get-user :body :user/cash)))
    (is (= [{:bet/payout         1010
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/false"
             :proposition/text   "Will Jon wipeout 2+ times this round?"}]
           (-> user2-get-user :body :user/notifications)))
    (is (= []
           (-> user2-get-user-notifs-acked :body :user/notifications)))))

(deftest can-bet-under-100-on-existing-bet
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        text "Will Jon wipeout 2+ times this round?"

        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        user-place-prop-bet-resp (user-place-prop-bet {:auth-token       auth-token
                                                       :projected-result true
                                                       :bet-amount       100
                                                       :channel-id channel-id})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       1
                                                        :channel-id channel-id})]))

(deftest cant-bet-under-100-no-existing-bet
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})

        text "Will Jon wipeout 2+ times this round?"
        create-prop-bet-resp (admin-create-prop {:auth-token auth-token
                                                 :text       text
                                                 :channel-id channel-id})

        user-place-prop-bet-resp2 (user-place-prop-bet {:auth-token       auth-token
                                                        :projected-result true
                                                        :bet-amount       1
                                                        :channel-id channel-id
                                                        :status           409})]))

(deftest event-reset-content-on-wrong-client
  (is (= 205 (:status
               ((common/test-app) (-> (mock/request :get "/stream/events/foo")
                                      (mock/header "client-version" "incorrect-value")))))))

(deftest www-redirect-to-non-www
  (let [{:keys [status headers]} ((common/test-app) (mock/request :get "http://www.localhost.com/"))]
    (is (= 301 status))
    (is (= "http://localhost.com/" (get headers "Location")))))

(deftest twitch-not-logged-in-user-is-gated
  (let [{:keys [auth-token] login-resp :response} (create-user-and-login
                                                    (assoc dummy-user :admin? true))

        title "Dirty Dan's Delirious Dance Party"
        channel-id "drdisrespect"
        create-event-resp (admin-create-event {:auth-token auth-token
                                               :title      title
                                               :channel-id channel-id})
        _ (twitch-ext-user-bet-on-10-props auth-token channel-id)
        user2-get-user (get-user {:auth-token nil :twitch-id? true})]

    (testing "user/gated? is true after user bets on 10 props"
      (is (true? (-> user2-get-user :body :user/gated?))))))

(deftest user-without-email-verification-no-bailout
  []
  (let [{:keys [auth-token]} (create-user-and-login (assoc dummy-user :admin? true))
        channel-id "drdisrespect"
        _ (admin-create-event {:auth-token auth-token
                               :title      "Dirty Dan's Delirious Dance Party"
                               :channel-id channel-id})
        _ (admin-create-prop {:auth-token auth-token
                              :text       "this is a propositon"
                              :channel-id channel-id})

        {user-auth-token :auth-token} (create-user-and-login (assoc dummy-user-2 :verify? false))

        _ (user-place-prop-bet {:auth-token       user-auth-token
                                :projected-result true
                                :bet-amount       500
                                :channel-id channel-id})
        _ (admin-end-prop {:auth-token auth-token
                           :result     "false"
                           :channel-id channel-id})
        get-user-resp (get-user {:auth-token user-auth-token})

        _ (verify-email {:email        (:email dummy-user-2)
                         :verify-token (-> common/test-state deref :emails first :user/verify-token)})

        get-user-resp2 (get-user {:auth-token user-auth-token})]
    (is (= 0 (-> get-user-resp :body :user/cash)))
    (is (= [#:notification{:type "notification.type/no-bailout"}]
           (-> get-user-resp :body :user/notifications)))

    (is (= 100 (-> get-user-resp2 :body :user/cash)))
    (is (= [#:notification{:type "notification.type/bailout"}]
           (-> get-user-resp2 :body :user/notifications)))))

(deftest mod-happy-path
  (testing "mods cant start or end events, but they can do everything else on the admin panel"
    (let [{:keys [auth-token]} (create-user-and-login (assoc dummy-user :admin? true))
          {mod-auth-token :auth-token} (create-user-and-login (assoc dummy-user-2 :mod? true))
          title "Dirty Dan's Delirious Dance Party"
          channel-id "drdisrespect"
          _ (admin-create-event {:auth-token mod-auth-token
                                 :title      title
                                 :channel-id channel-id
                                 :status 403})
          _ (admin-create-event {:auth-token auth-token
                                 :title      title
                                 :channel-id channel-id})

          _ (admin-create-prop {:auth-token mod-auth-token
                                :text       "this is a propositon"
                                :channel-id channel-id})
          _ (user-submit-suggestion {:auth-token mod-auth-token
                                     :text       "foo"
                                     :channel-id channel-id})

          get-suggestions-resp (admin-get-suggestions {:auth-token mod-auth-token
                                                       :channel-id channel-id})
          suggestions-to-dismiss (->> get-suggestions-resp
                                      :body
                                      (filter #(= (:suggestion/text %) "foo"))
                                      (mapv :suggestion/uuid))

          _ (admin-dismiss-suggestions {:auth-token  mod-auth-token
                                        :suggestions suggestions-to-dismiss
                                        :channel-id channel-id})
          _ (admin-end-prop {:auth-token mod-auth-token
                             :result     "false"
                             :channel-id channel-id})
          _ (admin-flip-prop-outcome {:auth-token mod-auth-token
                                      :channel-id channel-id})
          _ (admin-end-event {:auth-token mod-auth-token
                              :channel-id channel-id
                              :status     403})
          _ (admin-end-event {:auth-token auth-token
                              :channel-id channel-id})])))

(deftest unmod-cant-control-panel
  (let [{:keys [auth-token]} (create-user-and-login (assoc dummy-user :admin? true))
        {mod-auth-token :auth-token} (create-user-and-login (assoc dummy-user-2 :mod? true))
        channel-id "drdisrespect"]
    (admin-create-event {:auth-token auth-token
                         :title      "Dirty Dan's Delirious Dance Party"
                         :channel-id channel-id})
    (d/transact (:conn db/datomic-cloud) {:tx-data [{:db/id [:user/email (:email dummy-user-2)]
                                                     :user/status :user.status/active}
                                                    {:db/id [:user/email (:email dummy-user)]
                                                     :user/status :user.status/active}]})
    (admin-create-prop {:auth-token mod-auth-token
                        :channel-id channel-id
                        :text       "this is a propositon"
                        :status 403})
    (admin-create-prop {:auth-token auth-token
                        :channel-id channel-id
                        :text       "this is a propositon"
                        :status 403})))

(def test-username "IYUJZORPCRAZWYSXDJVNSLITTEPWKXQS")
(def test-token "VXFOGLGUSETVZPECHRGTGLPECPNOEAON")

;; TODO flesh tests out further, especially with more real data
(deftest csgo-game-state-wrong-password
  (let [response ((common/test-app) (-> (mock/request :post (format "/v1/gs/csgo/%s" test-username))
                                        (mock/json-body {:auth {:token "WROOOOONG"}})))]
    (is (= 401 (:status response)))))

(deftest csgo-game-state-no-event
  (let [response ((common/test-app) (-> (mock/request :post (format "/v1/gs/csgo/%s" test-username))
                                        (mock/json-body {:test "test"
                                                         :auth {:token test-token}})))]
    (is (= 204 (:status response)))))

(deftest csgo-game-state-wrong-channel-id
  (let [{:keys [auth-token]} (create-user-and-login (assoc dummy-user :admin? true))]
    (admin-create-event {:auth-token auth-token
                         :title      "Dirty Dan's Delirious Dance Party"
                         :channel-id "drdisrespect"})
    (post-csgo-game-state {:channel-id test-username
                           :token test-token
                           :message-type :round/begin
                           :status 204})))

(deftest csgo-game-state-wrong-event-type
  (let [{:keys [auth-token]} (create-user-and-login (assoc dummy-user :admin? true))]
    (admin-create-event {:auth-token auth-token
                         :source "none"
                         :title      "Dirty Dan's Delirious Dance Party"
                         :channel-id test-username})
    (post-csgo-game-state {:channel-id test-username
                           :token test-token
                           :message-type :round/begin
                           :status 204})))

(deftest csgo-game-state-auto-run-off
  (let [{:keys [auth-token]} (create-user-and-login (assoc dummy-user :admin? true))]
    (admin-create-event {:auth-token auth-token
                         :title      "Dirty Dan's Delirious Dance Party"
                         :channel-id test-username})
    (post-csgo-game-state {:channel-id test-username
                           :token test-token
                           :message-type :round/begin
                           :status 204})))

(deftest csgo-game-state-happy-path
  (testing "endpoint always return 2xx for csgo game client"
    (let [{:keys [auth-token]} (create-user-and-login (assoc dummy-user :admin? true))
          channel-id test-username
          title "Dirty Dan's Delirious Dance Party"
          _ (admin-create-event {:auth-token auth-token
                                 :title title
                                 :channel-id channel-id})
          _ (patch-event {:channel-id channel-id
                           :auto-run  "csgo"
                           :status    403})
          _ (patch-event {:channel-id  channel-id
                           :auto-run   "csgo"
                           :auth-token auth-token})
          get-event (get-event {:channel-id channel-id})
          create-response (post-csgo-game-state {:channel-id channel-id
                                                 :token test-token
                                                 :message-type :round/begin
                                                 :status 201})
          create-response-fail (post-csgo-game-state {:channel-id channel-id
                                                      :token test-token
                                                      :message-type :round/begin
                                                      :status 204})

          _ (user-place-prop-bet {:auth-token       auth-token
                                  :projected-result true
                                  :bet-amount       500
                                  :channel-id channel-id})

          end-response (post-csgo-game-state {:channel-id channel-id
                                              :token test-token
                                              :message-type :round/end-t})

          get-user-resp (get-user {:auth-token auth-token})

          end-response-fail (post-csgo-game-state {:channel-id channel-id
                                                   :token test-token
                                                   :message-type :round/end-t
                                                   :status 204})

          ;; get prop, check prev prop outcome, assert they match

          create-response2 (post-csgo-game-state {:channel-id channel-id
                                                  :token test-token
                                                  :message-type :round/begin
                                                  :status 201})

          _ (user-place-prop-bet {:auth-token       auth-token
                                  :projected-result false
                                  :bet-amount       510
                                  :channel-id channel-id})

          end-response2 (post-csgo-game-state {:channel-id channel-id
                                               :token test-token
                                               :message-type :round/end-ct})

          get-user-resp2 (get-user {:auth-token auth-token})

          create-response3 (post-csgo-game-state {:channel-id channel-id
                                                  :token test-token
                                                  :message-type :round/begin
                                                  :status 201})

          _ (user-place-prop-bet {:auth-token       auth-token
                                  :projected-result false
                                  :bet-amount       1530
                                  :channel-id channel-id})

          end-response3 (post-csgo-game-state {:channel-id channel-id
                                               :token test-token
                                               :message-type :round/end-t})

          get-user-resp3 (get-user {:auth-token auth-token})]

      (is (= #:event{:auto-run      "event.auto-run/csgo"
                     :running?      true
                     :title         title
                     :stream-source "event.stream-source/twitch"
                     :channel-id    (string/lower-case channel-id)}
             (dissoc (:body get-event) :event/start-time)))
      (is (= 1010 (-> get-user-resp :body :user/cash)))
      (is (= [{:bet/payout         1010
               :notification/type  "notification.type/payout"
               :proposition/result "proposition.result/true"
               :proposition/text   "Terrorists win this round"}]
             (-> get-user-resp :body :user/notifications)))

      (is (= 1530 (-> get-user-resp2 :body :user/cash)))
      (is (= [{:bet/payout         1030
               :notification/type  "notification.type/payout"
               :proposition/result "proposition.result/false"
               :proposition/text   "Terrorists win this round"}]
             (-> get-user-resp2 :body :user/notifications)))

      (is (= 100 (-> get-user-resp3 :body :user/cash)))
      (is (= [#:notification{:type "notification.type/bailout"}]
             (-> get-user-resp3 :body :user/notifications))))))

(deftest twitch-username-lookup
  (let [response ((common/test-app) (-> (mock/request :get "/twitch/user-id-lookup")
                                        ;;Huddy's id
                                        (mock/header "x-twitch-user-id" "207580146")))]
    (is (= 200 (:status response)))
    (is (= {:login "huddlesworth"} (common/parse-json-body response)))))

(deftest options-twitch-username-lookup
  (testing "need this endpoint and headers for CORS (twitch extension)"
    (let [resp ((common/test-app) (-> (mock/request :options "/twitch/user-id-lookup")))]
      (is (= 204
             (:status resp)))
      (is (= {"Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID, X-Twitch-User-ID"
              "Access-Control-Allow-Methods" "GET"
              "Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
              "Cache-Control"                "max-age=86400"
              "Content-Type"                 "application/octet-stream"
              "X-Content-Type-Options"       "nosniff"
              "X-Frame-Options"              "SAMEORIGIN"
              "X-XSS-Protection"             "1; mode=block"}
             (:headers resp))))))

;; TODO: make test to prove that em doesnt mess with other stream sources and doesnt mess with event.auto-run/none
(deftest event-manager-cancel-outstanding-prop
  (em/maybe-start-or-stop-csgo-events)
  (let [{:keys [auth-token]} (create-user-and-login dummy-user)
        events-resp (get-events)
        _ (em/maybe-start-or-stop-csgo-events)
        events-resp-again (get-events)
        create-prop-response (post-csgo-game-state {:channel-id test-username
                                                    :token test-token
                                                    :message-type :round/begin
                                                    :status 201})

        _ (user-place-prop-bet {:auth-token       auth-token
                              :projected-result false
                              :bet-amount       500
                              :channel-id test-username})
        user (get-user {:auth-token auth-token})

        _ (binding [common/*twitch-streams-live?* false]
            (em/maybe-start-or-stop-csgo-events))
        user2 (get-user {:auth-token auth-token})
        events-resp-over (get-events {:status 204})]
    (is (= [#:event{:auto-run      "event.auto-run/csgo"
                    :channel-id    (string/lower-case test-username)
                    :running?      true
                    :stream-source "event.stream-source/twitch"
                    :title         "STUCK IN SILVER 3 AAAAAAAAAAAAAA"}]
           (->> events-resp :body (map #(dissoc % :event/start-time)))
           (->> events-resp-again :body (map #(dissoc % :event/start-time)))))

    (is (= 0 (-> user
                 :body
                 :user/cash)))
    (is (= [] (-> user :body :user/notifications)))
    (is (= 500 (-> user2
                   :body
                   :user/cash)))
    (is (= [{:bet/payout         500
             :notification/type  "notification.type/payout"
             :proposition/result "proposition.result/cancelled"
             :proposition/text   "Terrorists win this round"}]
           (-> user2 :body :user/notifications)))))
