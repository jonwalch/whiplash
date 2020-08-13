(ns whiplash.constants)

;; TODO: in local dev change Access-Control-Allow-Origin to http://localhost:63342
(def ^:const CORS-GET-headers
  {"Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
   "Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID, X-Twitch-User-ID"
   "Access-Control-Allow-Methods" "GET"})

;(def ^:const CORS-GET-headers-allow-creds
;  {"Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
;   "Access-Control-Allow-Headers" "Origin, Content-Type, Accept"
;   "Access-Control-Allow-Credentials" "true"
;   "Access-Control-Allow-Methods" "GET"})

(def ^:const CORS-GET-and-POST-headers
  {"Access-Control-Allow-Origin"  "https://0ntgqty6boxxg10ghiw0tfwdc19u85.ext-twitch.tv"
   "Access-Control-Allow-Headers" "Origin, Content-Type, Accept, X-Twitch-Opaque-ID, X-Twitch-User-ID"
   "Access-Control-Allow-Methods" "POST, GET"})
