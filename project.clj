(defproject event-data-twitter-agent "0.1.13"
  :description "Event Data Twitter Agent"
  :url "http://eventdata.crossref.org"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :plugins [[lein-localrepo "0.5.3"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.391"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.crossref.event-data-agent-framework "0.1.16"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.1"]
                 [org.apache.logging.log4j/log4j-core "2.7"]
                 [robert/bruce "0.8.0"]
                 [throttler "1.0.0"]
                 [commons-codec/commons-codec "1.10"]
                 [event-data-common "0.1.16"]]
  :main ^:skip-aot event-data-twitter-agent.core
  :target-path "target/%s"
  :jvm-opts ["-Duser.timezone=UTC"]
  :profiles {:uberjar {:aot :all}
             :prod {:resource-paths ["config/prod" "resources"]}
             :dev {:resource-paths ["config/dev" "resources"]}})
