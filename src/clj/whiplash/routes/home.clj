(ns whiplash.routes.home
  (:require
    [whiplash.layout :as layout]
    [clojure.java.io :as io]
    [whiplash.middleware :as middleware]
    [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page [request]
  (layout/render request "about.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/graphiql" {:get (fn [request]
                        (layout/render request "graphiql.html"))}]
   ["/about" {:get about-page}]])

