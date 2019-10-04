(ns whiplash.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [whiplash.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[whiplash started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[whiplash has shut down successfully]=-"))
   :middleware wrap-dev})
