(defproject event-data-twitter-agent "0.1.3"
  :description "Event Data Twitter Agent"
  :url "http://eventdata.crossref.org"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :plugins [[lein-localrepo "0.5.3"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.391"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.crossref.event-data-agent-framework "0.1.8"]
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
