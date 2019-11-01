(ns whiplash.integrations.amazon-ses
  (:require [postal.core :as postal]))

(def aws-ses-config
  {:user "AKIAUJ3FEDZ5VOZNAGEU"
   :pass "BENLETvJ/OVvLIOdQjG/veYBsd3aLBDatLiEQ4UlXfYM"
   :host "email-smtp.us-west-2.amazonaws.com"
   :port 25
   :tls true})

(comment
  (postal/send-message aws-ses-config
                       {:from "noreply@whiplashesports.com"
                        :to ["jonwalch@gmail.com"]
                        :cc nil
                        :subject "Testeroni"
                        :body "Hi"})

  )
