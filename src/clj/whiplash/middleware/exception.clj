(ns whiplash.middleware.exception
  (:require [clojure.tools.logging :as log]
            [expound.alpha :as expound]
            [reitit.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [raven-clj.core :as rc]
            [raven-clj.interfaces :as ri]
            [whiplash.config :refer [env]]))

(defn coercion-error-handler [status]
  (let [printer (expound/custom-printer {:print-specs? false})]
    (fn [exception request]
      {:status status
       :headers {"Content-Type" "text/html"}
       :body (with-out-str (printer (-> exception ex-data :problems)))})))

(def ^:private dsn "https://213e4c48ffd244368adfbb93f7352fbd@o404694.ingest.sentry.io/5269183")

(defn report-to-sentry
  [e]
  (if (:prod env)
    (rc/capture dsn (ri/stacktrace {:message (.getMessage e)} e))
    (log/info "Not sending error to sentry because we're not in prod.")))

(def exception-middleware
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {;; log stack-traces for all exceptions
     ::exception/wrap (fn [handler e request]
                        (log/error e (.getMessage e))
                        (report-to-sentry e)
                        (handler e request))
     ;; human-optimized validation messages
     ::coercion/request-coercion (coercion-error-handler 400)
     ::coercion/response-coercion (coercion-error-handler 500)})))
