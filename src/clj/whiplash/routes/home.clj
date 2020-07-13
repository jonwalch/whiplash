(ns whiplash.routes.home
  (:require
    [whiplash.layout :as layout]
    [whiplash.middleware :as middleware]
    [whiplash.routes.services.event :as event]
    [whiplash.routes.services.proposition :as proposition]
    [whiplash.routes.services.leaderboard :as leaderboard]
    [whiplash.routes.services.user :as user]
    [whiplash.routes.services.suggestion :as suggestion]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.coercion :as coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [whiplash.middleware.exception :as exception]
    [reitit.ring.middleware.parameters :as parameters]
    [whiplash.middleware.formats :as formats]
    [reitit.coercion.spec :as spec-coercion]
    [whiplash.constants :as constants]))

(defn home-page [request]
  (layout/render request "index.html")
  #_(layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

#_(defn twitch-extension-page [request]
  (layout/render request "twitch-extension.html"))

(defn- CORS-GET-options
  [req]
  {:status  204
   :headers (merge constants/CORS-GET-headers {"Cache-Control" "max-age=86400"}) ;; Cache the options req for a day
   :body    nil})

(defn CORS-GET-and-POST-options
  [req]
  {:status  204
   ;; Cache the options req for a day
   :headers (merge constants/CORS-GET-and-POST-headers {"Cache-Control" "max-age=86400"})
   :body    nil})

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
   ["/leaderboard" {:get home-page}]
   ["/control" {:get home-page}]

   ;; admin only endpoints
   ["/admin"
    ["/event"
     [""
      {:post {:summary    "Create a new event"
              :middleware [middleware/wrap-admin]
              :parameters {:body {:title       string?
                                  :channel-id string?
                                  :source string?}}
              :handler    (fn [req]
                            (event/create-event req))}}]

     ["/end"
      {:post {:summary "End the current event"
              :middleware [middleware/wrap-admin]
              :handler (fn [req]
                         (event/end-current-event req))}}]
     ["/countdown"
      {:post {:summary "Set the timestamp of the next event to show countdown on home page"
              :middleware [middleware/wrap-admin]
              :parameters {:body {:ts string?}}
              :handler (fn [req]
                         (event/create-countdown req))}}]]

    ["/prop"
     [""
      {:post {:summary    "Create a new proposition"
              :middleware [middleware/wrap-admin]
              :parameters {:body {:text string?
                                  :end-betting-secs int?}}
              :handler    (fn [req]
                            (proposition/admin-create-proposition req))}}]
     ["/end"
      [""
       {:post {:summary    "End the current proposition"
               :middleware [middleware/wrap-admin]
               :parameters {:body {:result string?}}
               :handler    (fn [req]
                             (proposition/end-current-proposition req))}}]]
     ["/flip-previous"
      {:post {:summary    "Flip the outcome of the previous proposition"
              :middleware [middleware/wrap-admin]
              :handler    (fn [req]
                            (proposition/flip-prev-prop-outcome req))}}]]

    ["/suggestion"
     {:get  {:summary    "get prop suggestions for current event"
             :middleware [middleware/wrap-admin]
             :handler    (fn [req]
                           (suggestion/get-suggestions req))}

      :post {:summary    "Dismiss prop suggestions"
             :middleware [middleware/wrap-admin]
             :parameters {:suggestions [string?]}
             :handler    (fn [req]
                           (suggestion/dismiss-suggestions req))}}]]

   ;;endpoints client talks to
   ["/stream"
    ["/event"
     {:get  {:summary    "Get the current event"
             :handler    (fn [req]
                           (event/get-current-event req))}}]

    ["/prop"
     {:options {:summary "Take care of CORS preflight"
                :handler (fn [req] (CORS-GET-options [req]))}
      :get     {:summary "Get the current prop bet"
                :handler (fn [req]
                           (proposition/get-current-proposition req))}}]]

   ["/leaderboard"
    ["/all-time"
     {:get {:summary "return the highest all time user cash"
            :handler (fn [req]
                       (leaderboard/all-time-top-ten req))}}]

    ["/prop-bets"
     {:get  {:summary    "get all prop bets for current event"
             :handler    (fn [req]
                           (leaderboard/get-prop-bets req))}}]
    ["/event"
     {:options {:summary "CORS preflight"
                :handler (fn [req] (CORS-GET-options req))}
      :get  {:summary    "get scores from current or most recent event"
             :handler    (fn [req]
                           (leaderboard/event-score-leaderboard req))}}]]

   ["/user"
    [""
     {:options {:summary "Take care of CORS preflight"
                :handler (fn [req] (CORS-GET-options [req]))}
      :get  {:summary    "get a user"
             :middleware [middleware/wrap-restricted-or-twitch]
             :handler    (fn [req] (user/get-user req))}}]

    ["/login"
     [""
      {:post {:summary    "login as user"
              :parameters {:body {:user_name string?
                                  :password  string?}}
              :handler    (fn [req]
                            (user/login req))}}]]

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
     [""
      {:post {:summary    "update a user's password"
              :parameters {:body {:password string?}}
              :middleware [middleware/wrap-restricted]
              :handler    (fn [req]
                            (user/update-password req))}}]

     ["/recover"
      ;; get routing handled in react
      {:get home-page
       :post {:summary    "Update password from recovery flow"
              :parameters {:body {:email string?
                                  :token string?
                                  :new_password string?}}
              :handler    (fn [req]
                            (user/account-recovery-set-new-password req))}}]

     ["/request-recovery"
      {:post {:summary    "Send email to user with recovery link"
              :parameters {:body {:user string?}}
              :handler    (fn [req]
                            (user/account-recovery req))}}]]

    ["/prop-bet"
     {:options {:summary "Take care of CORS preflight"
                :handler (fn [req] (CORS-GET-and-POST-options [req]))}

      :get     {:summary    "get any current prop bets"
                :middleware [middleware/wrap-restricted]
                :handler    (fn [req]
                              (user/get-prop-bets req))}

      :post    {:summary    "create a guess for a user"
                :parameters {:body {:projected_result boolean?
                                    :bet_amount       int?}}
                :middleware [middleware/wrap-restricted-or-twitch]
                :handler    (fn [req]
                              (assoc (user/create-prop-bet req) :headers constants/CORS-GET-and-POST-headers))}}]
    ["/suggestion"
     {:post {:summary    "create a prop bet suggestion"
             :parameters {:body {:text string?}}
             :middleware [middleware/wrap-restricted]
             :handler    (fn [req]
                           (user/create-suggestion req))}}]]])
