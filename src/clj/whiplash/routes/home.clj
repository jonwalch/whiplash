(ns whiplash.routes.home
  (:require
    [whiplash.layout :as layout]
    [whiplash.middleware :as middleware]
    [ring.util.http-response :as response]
    [whiplash.routes.services.stream :as stream]
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

(defn about-page [request]
  (layout/render request "about.html"))

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

                 #_middleware/wrap-csrf
                 middleware/wrap-formats]}
   ;; user pages
   ["/" {:get home-page}]

   ;;endpoints client talks to
   ["/stream"
    {:get {:summary "get the current best stream candidate"
           :handler (fn [req]
                      (stream/get-stream req))}}]

   ["/leaderboard"
    ["/all-time"
     {:get {:summary "return this highest all time user cash"
            :handler (fn [req]
                       (leaderboard/all-time-top-ten req))}}]
    ["/weekly"
     {:get {:summary "return this week's leaderboard"
            :handler (fn [req]
                       (leaderboard/weekly-leaderboard req))}}]
    ["/bets"
     {:get  {:summary    "get all bets for current game"
             :parameters {:query {:game_id  int?
                                  :match_id int?}}
             :handler    (fn [req]
                           (leaderboard/get-bets req))}}]]

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

    ["/guess"
     {:get  {:summary    "get a guess for a user/game-id"
             :parameters {:query {:game_id  int?
                                  :match_id int?}}
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/get-bet req))}

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
    ]

   #_["/graphiql" {:get (fn [request]
                        (layout/render request "graphiql.html"))}]
   #_["/about" {:get about-page}]])

(comment (home-routes))
