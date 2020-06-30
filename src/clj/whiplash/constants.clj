(ns whiplash.constants)

(def ^:const CORS-GET-headers
  ;;TODO restrict to twitch.tv
  ;; TODO only some endpoints need X-Twitch-Opaque-ID
  {"Access-Control-Allow-Origin"  "*"
   "Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID"
   "Access-Control-Allow-Methods" "GET"})

(def ^:const CORS-GET-and-POST-headers
  ;;TODO restrict to twitch.tv
  {"Access-Control-Allow-Origin"  "*"
   "Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID"
   "Access-Control-Allow-Methods" "POST, GET"})
