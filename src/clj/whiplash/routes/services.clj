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
   ["" {:no-doc true
        :swagger {:info {:title "my-api"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/v1/swagger.json"
              :config {:validator-url nil}})}]]

   ;["/graphql" {:post (fn [req]
   ;                     (ok (graphql/execute-request (-> req :body slurp))))}]

   ["/user"
    {:swagger {:tags ["User"]}}

    ["/create"
     {:post    {:summary    "create a user"
                :parameters {:body {:first-name string?
                                    :last-name  string?
                                    :email      string?
                                    :password   string?}}
                :handler    (fn [req]
                              (user/create-user req))}}]

    ["/login"
     {:post {:summary    "login as user"
             :parameters {:body {:email    string?
                                 :password string?}}
             :handler    (fn [req]
                           (user/login req))}
      :get  {:summary    "get a user"
             :middleware [middleware/wrap-restricted]
             ;; TODO figure out how we want to get a user, by db/id?
             :parameters {:query {:email string?}}
             :handler    (fn [req]
                           (user/get-user req))}}]]

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
