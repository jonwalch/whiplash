(ns whiplash.routes.services
  (:require
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [whiplash.routes.services.graphql :as graphql]
    [whiplash.middleware.formats :as formats]
    [whiplash.middleware.exception :as exception]
    [whiplash.routes.services.user :as user]
    [whiplash.routes.services.stream :as stream]
    [whiplash.routes.services.leaderboard :as leaderboard]
    [ring.util.http-response :refer :all]
    [whiplash.middleware :as middleware]))

(defn service-routes []
  ["/v1"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api}
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
                 multipart/multipart-middleware]}

   ;; swagger documentation
   ["" #_{:no-doc true
        :swagger {:info {:title "my-api"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    #_["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    #_["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/v1/swagger.json"
              :config {:validator-url nil}})}]]

   ;["/graphql" {:post (fn [req]
   ;                     (ok (graphql/execute-request (-> req :body slurp))))}]

   ["/stream"
    {:get  {:summary    "get the current best stream candidate"
            :handler    (fn [req]
                          (stream/get-stream req))}}]

   ["/leaderboard"
    ["/weekly"
     {:get  {:summary    "return this week's leaderboard"
             :handler    (fn [req]
                           (leaderboard/weekly-leaderboard req))}}]]

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
             :handler    (constantly (ok {}))}

      :post {:summary    "login as user"
             :parameters {:body {:screen_name    string?
                                 :password string?}}
             :handler    (fn [req]
                           (user/login req))}}]

    ["/logout"
     {:post {:summary    "logout"
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/logout req))}}]

    ["/create"
     {:post    {:summary    "create a user"
                :parameters {:body {:first_name string?
                                    :last_name  string?
                                    :email      string?
                                    :password   string?
                                    :screen_name string?}}
                :handler    (fn [req]
                              (user/create-user req))}}]

    ["/guess"
     {:get {:summary    "get a guess for a user/game-id"
            :parameters {:query {:game_id int?
                                 :match_id int?}}
            :middleware [middleware/wrap-restricted]
            :handler    (fn [req]
                          (user/get-guess req))}

      :post {:summary    "create a guess for a user"
             :parameters {:body {:match_name string?
                                 :game_id int?
                                 :match_id int?
                                 :team_name string?
                                 :team_id int?}}
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/create-guess req))}}]]

   ["/math"
    {:swagger {:tags ["math"]}}

    ["/plus"
     {:get {:summary "plus with spec query parameters"
            :parameters {:query {:x int?, :y int?}}
            :responses {200 {:body {:total pos-int?}}}
            :handler (fn [{{{:keys [x y]} :query} :parameters}]
                       {:status 200
                        :body {:total (+ x y)}})}
      :post {:summary "plus with spec body parameters"
             :parameters {:body {:x int?, :y int?}}
             :responses {200 {:body {:total pos-int?}}}
             :handler (fn [{{{:keys [x y]} :body} :parameters}]
                        {:status 200
                         :body {:total (+ x y)}})}}]]

   #_["/files"
    {:swagger {:tags ["files"]}}

    ["/upload"
     {:post {:summary "upload a file"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :responses {200 {:body {:name string?, :size int?}}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        {:status 200
                         :body {:name (:filename file)
                                :size (:size file)}})}}]

    ["/download"
     {:get {:summary "downloads a file"
            :swagger {:produces ["image/png"]}
            :handler (fn [_]
                       {:status 200
                        :headers {"Content-Type" "image/png"}
                        :body (-> "public/img/warning_clojure.png"
                                  (io/resource)
                                  (io/input-stream))})}}]]])
