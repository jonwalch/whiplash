(ns whiplash.db.schemas)

(def ^:private schemas
  ;; Game betting MVP
  {:0 [{:db/doc         "User first name"
        :db/ident       :user/first-name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User last name"
        :db/ident       :user/last-name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User email address"
        :db/ident       :user/email
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        :db/unique      :db.unique/identity}

       {:db/doc         "Username"
        :db/ident       :user/name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        :db/unique      :db.unique/identity}

       {:db/doc         "User password"
        :db/ident       :user/password
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User status"
        :db/ident       :user/status
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User verify email token"
        :db/ident       :user/verify-token
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User's cash"
        :db/ident       :user/cash
        :db/valueType   :db.type/bigint
        :db/cardinality :db.cardinality/one}

       {:db/ident :user.status/pending}
       {:db/ident :user.status/active}
       {:db/ident :user.status/inactive}

       {:db/doc         "Team name"
        :db/ident       :team/name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Team id (from pandascore)"
        :db/ident       :team/id
        :db/valueType   :db.type/long
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Game type"
        :db/ident       :game/type
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one}

       {:db/ident :game.type/csgo}

       {:db/doc         "Match id (from pandascore)"
        :db/ident       :match/id
        :db/valueType   :db.type/long
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Game id (from pandascore)"
        :db/ident       :game/id
        :db/valueType   :db.type/long
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Match name (from pandascore)"
        :db/ident       :match/name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Bet amount"
        :db/ident       :bet/amount
        :db/valueType   :db.type/bigint
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Bet payout"
        :db/ident       :bet/payout
        :db/valueType   :db.type/bigint
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Time of bet"
        :db/ident       :bet/time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Bet processed yet"
        :db/ident       :bet/processed?
        :db/valueType   :db.type/boolean
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Bet processed time"
        :db/ident       :bet/processed-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User bets"
        :db/ident       :user/bets
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/isComponent true}

       {:db/doc         "Game bet pool for a team"
        :db/ident       :game/team-pool
        :db/valueType   :db.type/bigint
        :db/cardinality :db.cardinality/one}]

   :1 [{:db/ident :user.status/admin}

       {:db/doc         "Prop bets"
        :db/ident       :user/prop-bets
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/isComponent true}

       {:db/doc         "Events that we've hosted"
        :db/ident       :whiplash/events
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/many}

       {:db/doc         "Prop bets that we showed to the users during the event"
        :db/ident       :event/propositions
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/isComponent true}

       {:db/doc         "Title of the event"
        :db/ident       :event/title
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       ;; TODO: properly retire
       {:db/doc         "twitch user to stream for duration of event"
        :db/ident       :event/twitch-user
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Is this event still running?"
        :db/ident       :event/running?
        :db/valueType   :db.type/boolean
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Event start time"
        :db/ident       :event/start-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Event end time"
        :db/ident       :event/end-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Is this the current proposition?"
        :db/ident       :proposition/running?
        :db/valueType   :db.type/boolean
        :db/cardinality :db.cardinality/one}

       {:db/doc         "The actual prop bet string"
        :db/ident       :proposition/text
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "The outcome of the prop bet"
        :db/ident       :proposition/result?
        :db/valueType   :db.type/boolean
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Proposition start time"
        :db/ident       :proposition/start-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Proposition end time"
        :db/ident       :proposition/end-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "When we stopped accepting bets for this proposition"
        :db/ident       :proposition/betting-end-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Reference to admin prop bet"
        :db/ident       :bet/proposition
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User's projected outcome of the proposition"
        :db/ident       :bet/projected-result?
        :db/valueType   :db.type/boolean
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User submitted suggestions for upcoming propositions"
        :db/ident       :event/suggestions
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/isComponent true}

       {:db/doc         "The actual user suggestion string"
        :db/ident       :suggestion/text
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Has this suggestion been dismissed by an admin?"
        :db/ident       :suggestion/dismissed?
        :db/valueType   :db.type/boolean
        :db/cardinality :db.cardinality/one}

       {:db/doc         "The time the user suggestion was submitted"
        :db/ident       :suggestion/submission-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "The time the admin dismissed the suggestion"
        :db/ident       :suggestion/dismissed-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Suggestion's uuid to surface to the admin tool"
        :db/ident       :suggestion/uuid
        :db/unique      :db.unique/identity
        :db/valueType   :db.type/uuid
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Reference to user that submitted the suggestion"
        :db/ident       :suggestion/user
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one}]

   :2 [{:db/doc         "Enum of where the stream is sourced from"
        :db/ident       :event/stream-source
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one}

       {:db/ident :event.stream-source/twitch}
       {:db/ident :event.stream-source/youtube}
       {:db/ident :event.stream-source/cnn-unauth}

       {:db/doc         "Time the user signed up"
        :db/ident       :user/sign-up-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Time that the user verified their email address"
        :db/ident       :user/verified-email-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       ;; TODO: handle this properly
       #_{:db/doc   "Rename :event/twitch-user to :event/channel-id"
        :db/id    :event/twitch-user
        :db/ident :event/channel-id}

       {:db/doc         "New attribute to replace event/twitch-user"
        :db/ident       :event/channel-id
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one}
       ]

   :3 [{:db/doc         "Notifications to show to the user"
        :db/ident       :user/notifications
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/isComponent true}

       {:db/doc         "Type of notification"
        :db/ident       :notification/type
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one}

       {:db/ident :notification.type/payout}
       {:db/ident :notification.type/bailout}

       {:db/doc         "Reference to entity (when applicable) that triggered this notification"
        :db/ident       :notification/trigger
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Has this notification already been acknowledged? (either by the user or automatically by code)"
        :db/ident       :notification/acknowledged?
        :db/valueType   :db.type/boolean
        :db/cardinality :db.cardinality/one}

       {:db/doc         "Time of acknowledgment"
        :db/ident       :notification/acknowledged-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}
       ]

   :4 [{:db/doc         "Time of next event"
        :db/ident       :whiplash/next-event-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}]})

(defn migrations->schema-tx
  []
  (->> schemas
       (mapcat val)
       vec))
