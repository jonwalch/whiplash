(ns whiplash.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[whiplash started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[whiplash has shut down successfully]=-"))
   :middleware identity})
