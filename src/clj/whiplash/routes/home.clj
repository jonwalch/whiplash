(ns whiplash.routes.home
  (:require
    [whiplash.layout :as layout]
    [whiplash.middleware :as middleware]
    [ring.util.http-response :as response]
    [whiplash.routes.services.stream :as stream]
    [whiplash.routes.services.event :as event]
    [whiplash.routes.services.proposition :as proposition]
    [whiplash.routes.services.leaderboard :as leaderboard]
    [whiplash.routes.services.user :as user]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.coercion :as coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [whiplash.middleware.exception :as exception]
    [reitit.ring.middleware.parameters :as parameters]
    [whiplash.middleware.formats :as formats]
    [reitit.coercion.spec :as spec-coercion]))

(defn home-page [request]
  (layout/render request "index.html")
  #_(layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn home-routes []
  [""
   {:coercion   spec-coercion/coercion
    :muuntaja   formats/instance
    :middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware

                 middleware/wrap-formats]}
   ;; user pages
   ["/" {:get home-page}]
   ["/about" {:get home-page}]
   ["/account" {:get home-page}]
   ["/control" {:get home-page}]

   ;; admin only endpoints
   ["/admin"
    ["/event"
     [""
      {:post {:summary    "Create a new event"
              :middleware [middleware/wrap-admin]
              :parameters {:body {:title       string?
                                  :twitch_user string?}}
              :handler    (fn [req]
                            (event/create-event req))}}]

     ["/end"
      {:post {:summary "End the current event"
              :middleware [middleware/wrap-admin]
              :handler (fn [req]
                         (event/end-current-event req))}}]]

    ["/prop"
     [""
      {:post {:summary    "Create a new prop bet"
              :middleware [middleware/wrap-admin]
              :parameters {:body {:text string?}}
              :handler    (fn [req]
                            (proposition/admin-create-prop-bet req))}}]
     ["/end"
      {:post {:summary "End the current prop bet"
              :middleware [middleware/wrap-admin]
              :parameters {:body {:result boolean?}}
              :handler (fn [req]
                         (proposition/end-current-prop-bet req))}}]]]

   ;;endpoints client talks to
   ["/stream"
    [""
     {:get {:summary "get the current best stream candidate"
            :handler (fn [req]
                       (stream/get-stream req))}}]

    ["/event"
     {:get  {:summary    "Get the current event"
             :handler    (fn [req]
                           (event/get-current-event req))}}]

    ["/prop"
     {:get {:summary    "Get the current prop bet"
            :handler    (fn [req]
                          (proposition/get-current-prop-bet req))}}]]

   ["/leaderboard"
    ["/all-time"
     {:get {:summary "return the highest all time user cash"
            :handler (fn [req]
                       (leaderboard/all-time-top-ten req))}}]
    ["/weekly"
     {:get {:summary "return this week's leaderboard"
            :handler (fn [req]
                       (leaderboard/weekly-leaderboard req))}}]

    ["/weekly-prop-bets"
     {:get  {:summary    "this week's prop bet leaderboard"
             :handler    (fn [req]
                           (leaderboard/weekly-prop-bet-leaderboard req))}}]
    ["/bets"
     {:get  {:summary    "get all bets for current game"
             :parameters {:query {:game_id  int?
                                  :match_id int?}}
             :handler    (fn [req]
                           (leaderboard/get-bets req))}}]
    ["/prop-bets"
     {:get  {:summary    "get all prop bets for current event"
             :handler    (fn [req]
                           (leaderboard/get-prop-bets req))}}]]

   ["/user"

    ;;Defined for "/user"
    [""
     {:get  {:summary    "get a user"
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/get-user req))}}]

    ["/login"
     {:get  {:summary    "is the user's cookie valid?"
             :middleware [middleware/wrap-restricted]
             ;; wrap-restricted will return unauthorized if the cookie is no longer valid
             :handler    (fn [req]
                           (response/ok {:user/name
                                         (middleware/authed-req->user-name req)}))}

      :post {:summary    "login as user"
             :parameters {:body {:user_name string?
                                 :password    string?}}
             :handler    (fn [req]
                           (user/login req))}}]

    ["/logout"
     {:post {:summary    "logout"
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/logout req))}}]

    ["/verify"
     ;; get routing handled in react
     {:get home-page

      :post {:summary    "verify email"
             :parameters {:body {:email string?
                                 :token    string?}}
             :handler    (fn [req]
                           (user/verify-email req))}}]

    ["/create"
     {:post    {:summary    "create a user"
                :parameters {:body {:first_name string?
                                    :last_name  string?
                                    :email      string?
                                    :password   string?
                                    :user_name string?}}
                :handler    (fn [req]
                              (user/create-user req))}}]

    ["/password"
     {:post    {:summary    "update a user's password"
                :parameters {:body {:password   string?}}
                :middleware [middleware/wrap-restricted]
                :handler    (fn [req]
                              (user/update-password req))}}]

    ["/guess"
     {:get  {:summary    "get a guess for a user/game-id"
             :parameters {:query {:game_id  int?
                                  :match_id int?}}
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/get-bets req))}

      :post {:summary    "create a guess for a user"
             :parameters {:body {:match_name string?
                                 :game_id    int?
                                 :match_id   int?
                                 :team_name  string?
                                 :team_id    int?
                                 :bet_amount int?}}
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/create-bet req))}}]

    ["/prop-bet"
     {:get  {:summary    "get any current prop bets"
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/get-prop-bets req))}

      :post {:summary    "create a guess for a user"
             :parameters {:body {:projected_result boolean?
                                 :bet_amount int?}}
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/create-prop-bet req))}}
     ]]])
