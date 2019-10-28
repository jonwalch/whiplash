(ns whiplash.middleware
  (:require
    [whiplash.env :refer [defaults]]
    [cheshire.generate :as cheshire]
    [cognitect.transit :as transit]
    [clojure.tools.logging :as log]
    [whiplash.layout :refer [error-page]]
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
    [whiplash.middleware.formats :as formats]
    [muuntaja.middleware :refer [wrap-format wrap-params]]
    [whiplash.config :refer [env]]
    [ring-ttl-session.core :refer [ttl-memory-store]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [buddy.auth.middleware :as buddy-middleware]
    [buddy.auth.accessrules :refer [restrict]]
    [buddy.auth :refer [authenticated?]]
    [buddy.auth.backends.token :refer [jwe-backend]]
    [buddy.sign.jwt :as jwt]
    [clj-time.core :refer [plus now days]]
    [buddy.core.hash :as hash]
    [whiplash.time :as time]))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        {:status 500
         :body   {:message "We've dispatched a team of highly trained gnomes to take care of the problem."}}
        #_(error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     {:status 403
      :body   {:message "Invalid anti-forgery token"}}
     #_(error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))

(defn wrap-formats [handler]
  (let [wrapped (-> handler wrap-params (wrap-format formats/instance))]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

;; TODO: read this from env var and set in K8S config
(def secret (hash/sha256 "HIILWUUQBSCCICRMTJSQXIRYUIJIJRRL"))

(defn authfn
  [token]
  token)

(defn jwe-on-error
  [request e]
  (log/info request e))

;; TODO revisit :alg and :enc, maybe add unauthorized-handler
(def token-backend
  (jwe-backend {:secret secret
                :token-name "Bearer"
                :authfn authfn
                :on-error jwe-on-error
                #_#_:options {:alg :a256kw
                          :enc :a128gcm}}))

;; TODO revisit :alg and :enc
(defn token [email]
  (let [exp (time/days-delta 30)
        claims {:user email
                :exp  (time/to-millis exp)}]
    {:token (jwt/encrypt claims secret #_{:alg :a256kw :enc :a128gcm})
     :exp-str (time/http-date-str exp)}))

#_(defn valid-token-auth?
  [{:keys [identity] :as req}]
  (let [{:keys [user exp]} identity]
    (boolean (and (string? user)
                  (int? exp)
                  (< (time/to-millis) exp)
                  (db/find-user-by-email user)))))

(defn req->token
  [req]
  (when-let [cookie-value (some-> req :cookies (get "value") :value)]
    ;; logout sets the cookie value to "deleted"
    (when (not= "deleted" cookie-value)
      (jwt/decrypt cookie-value secret))))

(defn valid-cookie-auth?
  [{:keys [cookies] :as req}]
  (let [{:keys [user exp]} (req->token req)]
    (boolean (and (string? user)
                  (int? exp)
                  (< (time/to-millis) exp)))))

(defn on-error [request response]
  {:status 403
   :body   {:message (str "Access to " (:uri request) " is not authorized")}}
  #_(error-page
    {:status 403
     :title (str "Access to " (:uri request) " is not authorized")}))

;; Add on a per route basis
(defn wrap-restricted [handler]
  (restrict handler {:handler valid-cookie-auth?
                     :on-error on-error}))

;; Will add :identity to request if passed in as properly formatted Authorization Header
(defn wrap-auth [handler]
  (let [backend token-backend]
    (-> handler
        (buddy-middleware/wrap-authentication backend)
        (buddy-middleware/wrap-authorization backend))))

;; These are applied in reverse order, how intuitive
(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-auth
      (wrap-defaults
        (-> site-defaults
            ;;TODO Double check home routes implement CSRF protection
            (assoc-in [:security :anti-forgery] false)
            #_(assoc-in  [:session :store] (ttl-memory-store (* 60 30))))) ;;seconds
      wrap-internal-error))

(comment
  (defn rand-str [len]
    (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))
  (rand-str 32)
  )
