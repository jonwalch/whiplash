(ns whiplash.db.schemas)

(def ^:private schemas
  {:0 [{:db/doc         "User first name"
        :db/ident       :user/first-name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "User last name"
        :db/ident       :user/last-name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "User email address"
        :db/ident       :user/email
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        :db/unique      :db.unique/identity
        }
       {:db/doc         "Username"
        :db/ident       :user/name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        :db/unique      :db.unique/identity
        }
       {:db/doc         "User password"
        :db/ident       :user/password
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "User status"
        :db/ident       :user/status
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "User verify email token"
        :db/ident       :user/verify-token
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        }
       ;; example of enumeration in Datomic
       {:db/ident :user.status/pending}
       {:db/ident :user.status/active}
       {:db/ident :user.status/inactive}

       {:db/doc         "Team name"
        :db/ident       :team/name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "Team id (from pandascore)"
        :db/ident       :team/id
        :db/valueType   :db.type/long
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "Game type"
        :db/ident       :game/type
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "Match id (from pandascore)"
        :db/ident       :match/id
        :db/valueType   :db.type/long
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "Game id (from pandascore)"
        :db/ident       :game/id
        :db/valueType   :db.type/long
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "Match name (from pandascore)"
        :db/ident       :match/name
        :db/valueType   :db.type/string
        :db/cardinality :db.cardinality/one
        }
       ;{:db/doc         "Teams that played this game"
       ; :db/ident       :game/teams
       ; :db/valueType   :db.type/ref
       ; :db/cardinality :db.cardinality/many
       ; }
       {:db/ident :game.type/csgo}

       {:db/doc         "Score of guess"
        :db/ident       :guess/score
        :db/valueType   :db.type/long
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "Time of guess"
        :db/ident       :guess/time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "Guess processed yet"
        :db/ident       :guess/processed?
        :db/valueType   :db.type/boolean
        :db/cardinality :db.cardinality/one
        }
       {:db/doc         "Guess processed time"
        :db/ident       :guess/processed-time
        :db/valueType   :db.type/instant
        :db/cardinality :db.cardinality/one}

       {:db/doc         "User guesses"
        :db/ident       :user/guesses
        :db/valueType   :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/isComponent true
        }
       ]})

(defn migrations->schema-tx
  []
  (->> schemas
       (mapcat val)
       vec))
