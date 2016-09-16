(ns event-data-twitter-agent.core
  (:require [org.crossref.event-data-agent-framework.core :as c]
            [org.crossref.event-data-agent-framework.util :as agent-util]
            [org.crossref.event-data-agent-framework.web :as agent-web]
            [crossref.util.doi :as cr-doi])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [reader]]
            [clojure.data.json :as json]
            [clojure.core.async :refer [thread buffer chan <!! >!!]])
  (:require [config.core :refer [env]]
            [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time]
            [throttler.core :refer [throttle-fn]])
  (:import [java.util.logging Logger Level])
  (:gen-class))

(def version "0.1.0")

(defn update-rules
  "Update the Gnip rules on a schedule."
  [evidence callback]
)

(defn ingest-stream
  "Run Gnip stream ingestion."
  [evidence callback]

)

(defn process-stream
  "Process inputs from Gnip"
  [evidence callback]
  
)

; The `update-rules` artifact fetches the most recent rules, updates Gnip and then saves the artifact info in the `domain-list-artifact` atom.
; It repeats this every hour.
; The `process-stream` waits for this to have a value before starting first time.

(def agent-definition
  {:agent-name "twitter-agent"
   :version version
   :schedule [{:name "update-rules"
              :fun update-rules
              :seconds (* 60 60)
              :required-artifacts ["domain-list"]}]
   :runners [{:name "ingest-stream"
              :fun ingest-stream
              :required-artifacts []}
             {:name "process-stream"
              :fun process-stream
              :threads 100
              :required-artifacts []}]
   :build-evidence (fn [input] nil)
   :process-evidence (fn [evidence] nil)})

(defn -main [& args]
  (c/run args agent-definition))