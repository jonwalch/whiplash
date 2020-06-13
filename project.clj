(defproject whiplash "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[buddy "2.0.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [cheshire "5.10.0"]
                 [clj-oauth "1.5.5"]
                 [clojure.java-time "0.3.2"]
                 [com.datomic/client-cloud "0.8.96"]
                 [com.google.guava/guava "25.1-jre"]
                 [cprop "0.1.16"]
                 [expound "0.8.4"]
                 [funcool/struct "1.4.0"]
                 #_[luminus-aleph "0.1.6"]
                 [luminus-undertow "0.1.5"]
                 [luminus-transit "0.1.2"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [markdown-clj "1.10.4"]
                 [metosin/muuntaja "0.6.6"]
                 [metosin/reitit "0.4.2"]
                 [metosin/ring-http-response "0.9.1"]
                 [mount "0.1.16"]
                 [nrepl "0.7.0"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.0.0"]
                 [org.webjars.npm/bulma "0.8.2"]
                 [org.webjars.npm/material-icons "0.3.1"]
                 [org.webjars/webjars-locator "0.39"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-defaults "0.3.2"]
                 [selmer "1.12.22"]

                 ;; manually added from here below
                 [clj-http "3.10.1"]
                 [danlentz/clj-uuid "0.1.9"]
                 [buddy/buddy-hashers "1.4.0"]
                 ;;[org.clojure/core.async "1.1.587"]
                 [com.draines/postal "2.0.3"]
                 [raven-clj "1.6.0"]
                 ]

  :min-lein-version "2.0.0"
  
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot whiplash.core

  :plugins []

  :profiles
  {:uberjar       {:omit-source    true
                   :aot            :all
                   :uberjar-name   "whiplash.jar"
                   :source-paths   ["env/prod/clj"]
                   :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev   {:jvm-opts       ["-Dconf=dev-config.edn" "-Xmx1g"]
                   :dependencies   [
                                    ;;[pjstadig/humane-test-output "0.9.0"]
                                    [prone "2020-01-17"]
                                    [ring/ring-devel "1.8.0"]
                                    [ring/ring-mock "0.4.0"]
                                    [datomic-client-memdb "1.0.2" :exclusions [org.slf4j/log4j-over-slf4j org.slf4j/slf4j-nop com.google.guava/guava]]]
                   :plugins        [[com.jakemccrary/lein-test-refresh "0.24.1"]]

                   :source-paths   ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"]
                   :repl-options   {:init-ns user}
                   ;:injections     [(require 'pjstadig.humane-test-output)
                   ;                 (pjstadig.humane-test-output/activate!)]
                   }
   :project/test  {:jvm-opts       ["-Dconf=test-config.edn"]
                   :resource-paths ["env/test/resources"]}
   :profiles/dev  {}
   :profiles/test {}})
