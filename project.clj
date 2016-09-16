(defproject event-data-twitter-agent "0.1.1"
:description "Event Data Twiter Agent"
  :url "http://eventdata.crossref.org"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.391"]
                 [org.crossref.event-data-agent-framework "0.1.6"]
                 [robert/bruce "0.8.0"]
                 [throttler "1.0.0"]
                 [com.twitter/hbc-core "2.2.0"]]
  :main ^:skip-aot event-data-twitter-agent.core
  :java-source-paths ["src-java"]
  :target-path "target/%s"
  :jvm-opts ["-Duser.timezone=UTC"]
  :profiles {:uberjar {:aot :all}
             :prod {:resource-paths ["config/prod"]}
             :dev {:resource-paths ["config/dev"]}})
