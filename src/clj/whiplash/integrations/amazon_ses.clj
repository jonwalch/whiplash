(ns whiplash.integrations.amazon-ses
  (:require [postal.core :as postal]
            [whiplash.config :refer [env]]
            [clojure.tools.logging :as log]))

(def aws-ses-config
  {:user "AKIAUJ3FEDZ5XK6Z7THM"
   :pass "BLF7jQ5tdWxsCTapjL/RZI6h25Ki2UNIqBhI4Mw2bnWX"
   :host "email-smtp.us-west-2.amazonaws.com"
   :port 587
   :tls true})

(defn- internal-send-verification-email
  [{:keys [user/email body subject]}]
  ;; On success save this email information to the user
  (if (:prod env)
    (future
      (try (postal/send-message aws-ses-config
                                {:from    "Whiplash <noreply@whiplashesports.com>"
                                 :to      email
                                 :cc      nil
                                 :subject subject
                                 :body    body})
           (catch Throwable t
             (log/error (format "Failed to send verification email to %s" email) t))))
    (log/info {:to      email
               :subject subject
               :body    body})))

(comment
  (internal-send-verification-email {:user/email "jonwalch@gmail.com" :subject "hi" :body "hi"}))

(defn send-verification-email
  [{:user/keys [email first-name verify-token] :as user}]
  (let [verify-url (format "https://www.whiplashesports.com/user/verify?email=%s&token=%s"
                           email
                           verify-token)
        body (format "Hi %s,\n\nPlease click this link to verify your email address %s\n\nYour buddies,\nWhiplash"
                     first-name
                     verify-url)]
    (internal-send-verification-email (merge user {:subject "Whiplash: Please verify your email!"
                                                   :body    body}))))
