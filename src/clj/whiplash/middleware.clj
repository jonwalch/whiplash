(ns whiplash.middleware
  (:require
    [whiplash.env :refer [defaults]]
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
    [whiplash.time :as time]
    [clojure.string :as string]
    [ring.util.request :as req]
    [whiplash.constants :as constants]
    [whiplash.db.core :as db]))

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

#_(defn wrap-csrf [handler]
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
(def ^:private secret (hash/sha256 "HIILWUUQBSCCICRMTJSQXIRYUIJIJRRL"))

(comment
  (defn rand-str [len]
    (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))
  (rand-str 32))

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
(defn token [user-name user-status]
  (let [exp (time/days-delta 30)
        claims {:user user-name
                :exp  (time/to-millis exp)
                :status user-status}]
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
  [{:keys [cookies] :as req}]
  (when-let [cookie-value (some-> cookies (get "value") :value)]
    (try
      (jwt/decrypt cookie-value secret)
      (catch Throwable t nil
                         #_(when (not= "deleted" cookie-value)
                           (log/error (format "Failed to decrypt JWT %s "
                                              cookie-value)
                                      nil))))))

(defn valid-cookie-auth?
  [req]
  (let [{:keys [user exp]} (req->token req)]
    (boolean (and (string? user)
                  (int? exp)
                  (< (time/to-millis) exp)))))

(defn valid-admin-auth?
  [req]
  (let [{:keys [user exp]} (req->token req)
        {:user/keys [status]} (db/pull-user {:user/name user :attrs [:user/status]})]
    (boolean (and (string? user)
                  (int? exp)
                  (< (time/to-millis) exp)
                  (= :user.status/admin status)))))

(defn valid-admin-or-mod-auth?
  [req]
  (let [{:keys [user exp]} (req->token req)
        {:user/keys [status]} (db/pull-user {:user/name user :attrs [:user/status]})]
    (boolean (and (string? user)
                  (int? exp)
                  (< (time/to-millis) exp)
                  (or (= :user.status/admin status)
                      (= :user.status/mod status))))))

#_(defn valid-auth-or-ga-cookie?
  [req]
  (let [{:keys [user exp]} (req->token req)]
    (or (boolean (and (string? user)
                      (int? exp)
                      (< (time/to-millis) exp)))
        ;; TODO: regex validation of _ga tag
        (string? (-> req :cookies (get "_ga") :value)))))

(defn valid-auth-or-twitch-opaque-id?
  [req]
  (let [{:keys [user exp]} (req->token req)]
    (or (boolean (and (string? user)
                      (int? exp)
                      (< (time/to-millis) exp)))
        ;; TODO: regex validation of _ga tag
        #_(string? (-> req :cookies (get "_ga") :value))
        (string? (-> req :headers (get "x-twitch-opaque-id"))))))

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

;; Add on a per route basis
#_(defn wrap-restricted-or-ga-unauth-user [handler]
  (restrict handler {:handler valid-auth-or-ga-cookie?
                     :on-error on-error}))

;; Add on a per route basis
(defn wrap-restricted-or-twitch [handler]
  (restrict handler {:handler valid-auth-or-twitch-opaque-id?
                     :on-error on-error}))

;; Add on a per route basis
(defn wrap-admin [handler]
  (restrict handler {:handler valid-admin-auth?
                     :on-error on-error}))

(defn wrap-admin-or-mod [handler]
  (restrict handler {:handler valid-admin-or-mod-auth?
                     :on-error on-error}))

;; Will add :identity to request if passed in as properly formatted Authorization Header
(defn wrap-auth [handler]
  (let [backend token-backend]
    (-> handler
        (buddy-middleware/wrap-authentication backend)
        (buddy-middleware/wrap-authorization backend))))

(defn wrap-www-redirect [handler]
  (fn [request]
    (let [url (req/request-url request)
          redirect-url (string/replace url #"://www\." "://")]
      (if (not= url redirect-url)
        {:status 301
         :headers {"Location" redirect-url
                   "Cache-Control" "max-age=86400"}         ;; cache for a day
         :body ""}
        (handler request)))))

;; These are applied in reverse order, how intuitive
(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-www-redirect
      wrap-auth
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            #_(assoc-in  [:session :store] (ttl-memory-store (* 60 30))))) ;;seconds
      wrap-internal-error))
