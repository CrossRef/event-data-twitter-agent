(ns event-data-twitter-agent.core
  (:require [event-data-twitter-agent.gnip :as gnip]
            [org.crossref.event-data-agent-framework.core :as core]
            [event-data-twitter-agent.rules :as rules]
            
            [config.core :refer [env]]
            [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time]
            [event-data-common.backoff :as backoff]
            [throttler.core :refer [throttle-fn]]
            [org.httpkit.client :as client])
  (:import [java.util UUID])
  (:gen-class))


(def agent-definition
  {:agent-name "twitter-agent"
   :version gnip/version
   :schedule []
   :runners [{:name "ingest"
              :fun gnip/run-ingest
              :required-artifacts []}
             {:name "send"
              :fun gnip/run-send
              :required-artifacts []}]})

(defn -main [& args]
  (if (= (first args) "update-rules")
    (rules/run-update-rules)
    (core/run agent-definition)))
