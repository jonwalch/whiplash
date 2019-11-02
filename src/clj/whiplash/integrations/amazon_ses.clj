(ns whiplash.integrations.amazon-ses
  (:require [postal.core :as postal]))

(def aws-ses-config
  {:user "AKIAUJ3FEDZ5VOZNAGEU"
   :pass "BENLETvJ/OVvLIOdQjG/veYBsd3aLBDatLiEQ4UlXfYM"
   :host "email-smtp.us-west-2.amazonaws.com"
   :port 25
   :tls true})

(defn- internal-send-verification-email
  [{:keys [user/email body subject]}]
  ;; On success save this email information to the user
  (postal/send-message aws-ses-config
                       {:from    "noreply@whiplashesports.com"
                        :to      "jonwalch@gmail.com"       ;;email
                        :cc      nil
                        :subject subject
                        :body    body}))

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
