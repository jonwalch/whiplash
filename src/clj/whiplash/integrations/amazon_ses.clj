(ns whiplash.integrations.amazon-ses
  (:require [postal.core :as postal]
            [whiplash.config :refer [env]]
            [clojure.tools.logging :as log]))

;;TODO move secrets to KMS
(defonce aws-ses-config
  {:user "AKIAUJ3FEDZ5XK6Z7THM"
   :pass "BLF7jQ5tdWxsCTapjL/RZI6h25Ki2UNIqBhI4Mw2bnWX"
   :host "email-smtp.us-west-2.amazonaws.com"
   :port 587
   :tls true})

(defn- internal-send-email
  [{:keys [user/email body subject email/type]}]
  ;; TODO: On success save this email information to the user
  (if (:prod env)
    (future
      ;;TODO: retry n times and then give up
      (try (postal/send-message aws-ses-config
                                {:from    "Whiplash <noreply@whiplashesports.com>"
                                 :to      email
                                 :cc      nil
                                 :subject subject
                                 :body    body})
           (catch Throwable t
             (log/error (format "Failed to send %s email to %s" type email) t))))
    (log/info {:to      email
               :subject subject
               :body    body})))

(defn- generate-url
  [url email token]
  (format "%s?email=%s&token=%s" url email token))

(defn send-verification-email
  [{:user/keys [email first-name verify-token] :as user}]
  (let [verify-url (generate-url "https://www.whiplashesports.com/user/verify" email verify-token)
        body (format "Hi %s,\n\nPlease click this link to verify your email address %s\n\nYour buddies,\nWhiplash"
                     first-name
                     verify-url)]
    (internal-send-email (merge user {:subject    "Whiplash: Please verify your email!"
                                      :body       body
                                      :email/type :email.type/verification}))))

(defn send-recovery-email
  [{:keys [user/email user/first-name recovery/token] :as user}]
  (let [verify-url (generate-url "https://www.whiplashesports.com/user/password/recover" email token)
        body (format "Hi %s,\n\nPlease click this link to reset your password %s\n\nYour buddies,\nWhiplash"
                     first-name
                     verify-url)]
    (internal-send-email (merge user {:subject    "Whiplash: Reset your password"
                                      :body       body
                                      :email/type :email.type/recovery}))))
