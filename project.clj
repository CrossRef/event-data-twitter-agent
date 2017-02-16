(defproject event-data-twitter-agent "0.1.7"
  :description "Event Data Twitter Agent"
  :url "http://eventdata.crossref.org"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :plugins [[lein-localrepo "0.5.3"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.391"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.crossref/event-data-agent-framework "0.1.9"]
                 ; [org.crossref.event-data-agent-framework "0.1.7"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.1"]
                 [org.apache.logging.log4j/log4j-core "2.7"]
                 [robert/bruce "0.8.0"]
                 [throttler "1.0.0"]
                 [commons-codec/commons-codec "1.10"]
                 [event-data-common "0.1.12"]
                 
                 ; For HBC
                 [org.apache.httpcomponents/httpclient "4.2.5"]
                 [com.google.guava/guava "14.0.1"]
                 [org.slf4j/slf4j-api "1.6.6"]
                 [com.twitter/joauth "6.0.2"]
                 [com.google.code.findbugs/jsr305 "3.0.1"]
                 [org.mockito/mockito-all "1.8.5"]
                 [junit/junit "4.8.1"]]
  :main ^:skip-aot event-data-twitter-agent.core
  :java-source-paths ["src-java"]
  :target-path "target/%s"
  :jvm-opts ["-Duser.timezone=UTC"]
  :profiles {:uberjar {:aot :all}
             :prod {:resource-paths ["config/prod" "resources"]}
             :dev {:resource-paths ["config/dev" "resources"]}})
