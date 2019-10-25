(ns whiplash.routes.home
  (:require
    [whiplash.layout :as layout]
    [clojure.java.io :as io]
    [whiplash.middleware :as middleware]
    [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "index.html")
  #_(layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page [request]
  (layout/render request "about.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/signup" {:get home-page}]
   ["/leaderboard" {:get home-page}]
   #_["/graphiql" {:get (fn [request]
                        (layout/render request "graphiql.html"))}]
   #_["/about" {:get about-page}]])
