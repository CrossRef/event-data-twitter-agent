(ns event-data-twitter-agent.core
  (:require [org.crossref.event-data-agent-framework.core :as c]
            [org.crossref.event-data-agent-framework.util :as agent-util]
            [org.crossref.event-data-agent-framework.web :as agent-web]
            [crossref.util.doi :as cr-doi]
            [event-data-twitter-agent.gnip :as gnip])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [reader]]
            [clojure.data.json :as json]
            [clojure.core.async :refer [thread buffer chan <!! >!!]])
  (:require [config.core :refer [env]]
            [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time]
            [throttler.core :refer [throttle-fn]])
  (:import [java.util.logging Level Logger]
           [java.util UUID])
  (:gen-class))

(def version "0.1.0")
(def source-token "45a1ef76-4f43-4cdc-9ba8-5a6ad01cc231")

(def gnip-input-buffer (buffer 2048))
(def gnip-input-stream (chan gnip-input-buffer))

(def current-domain-list-artifact-url (atom nil))
(def current-doi-prefix-list-artifact-url (atom nil))

(defn update-rules
  "Update the Gnip rules on a schedule."
  [artifacts callback]
  (let [[domain-list-url domain-list-file] (get artifacts "domain-list")
        [doi-prefix-list-url prefix-list-file] (get artifacts "doi-prefix-list")]
    
    (gnip/update-all-files prefix-list-file domain-list-file)
    
    (reset! current-domain-list-artifact-url domain-list-url)
    (reset! current-doi-prefix-list-artifact-url doi-prefix-list-url)))

(defn ingest-stream
  "Run Gnip stream ingestion."
  [artifacts callback]
  (gnip/run-stream gnip-input-stream))

(defn process-stream
  "Process inputs from Gnip. Send Evidence Records."
  [artifacts callback]
  (log/info "Await updated rules artifact URL...")
  (await @current-domain-list-artifact-url
    (log/info "Got rules! Start processing")
    (loop [input-event (<!! gnip-input-stream)]
      (c/send-heartbeat "twitter-agent/input/process-stream-event" 1)
      (let [doi-attempts (map agent-web/query-reverse-api (:urls input-event))
            doi-matches (filter :doi doi-attempts)
            deposits (map (fn [doi-match]     
                             {:uuid (str (UUID/randomUUID))
                              :source_token source-token
                              :subj_id (:tweet-url input-event)
                              :obj_id (cr-doi/normalise-doi (:doi doi-match))
                              :relation_type_id "discusses"
                              :source_id "twitter"
                              :action "add"
                              :occurred_at (str (:posted-time input-event))
                              :subj {:title (:body input-event)
                                     :author {:literal (:author input-event)}
                                     :issued (str (:posted-time input-event))
                                     :pid (:tweet-url input-event)
                                     :URL (:tweet-url input-event)
                                     :type "tweet"}})
                              doi-matches)
            
            evidence-record {:artifacts [@current-domain-list-artifact-url @current-doi-prefix-list-artifact-url]
                             :input (select-keys [:tweet-url :author :posted-time :body :urls :matching-rules] input-event)
                             :agent {:name "twitter" :version version}
                             :working {:matching-rules (:matching-rules input-event)
                                       :matching-dois doi-matches
                                       :match-attempts doi-attempts
                                       :original-author (:original-author input-event)
                                       :original-tweet-url (:original-tweet-url input-event)}
                             :deposits deposits}]
        
        (when (> (count doi-matches) 0)
          (c/send-heartbeat "twitter-agent/process/found-dois" (count doi-matches)))
        
        (callback evidence-record)
        (prn evidence-record))
      
      (recur (<!! gnip-input-stream)))))




(defn report-queue-sizes
  "Periodically report size of queues."
  [artifacts callback]
  (prn "GNIP " (count gnip-input-buffer))
  (c/send-heartbeat "twitter-agent/input/input-queue" (count gnip-input-buffer))
  ; (reset! heartbeat-recent-changes-stream 0)
  ; (c/send-heartbeat "wikipedia-agent/process/process-input" @heartbeat-process-input)
  ; (reset! heartbeat-process-input 0)
  
  ; (c/send-heartbeat "wikipedia-agent/restbase-input/query" @process/heartbeat-restbase)
  ; (reset! process/heartbeat-restbase 0)
  
  ; (c/send-heartbeat "wikipedia-agent/restbase-input/ok" @process/heartbeat-restbase-ok)
  ; (reset! process/heartbeat-restbase-ok 0)
  
  ; (c/send-heartbeat "wikipedia-agent/restbase-input/error" @process/heartbeat-restbase-error)
  ; (reset! process/heartbeat-restbase-error 0)
  )
  

; The `update-rules` artifact fetches the most recent rules, updates Gnip and then saves the artifact info in the `domain-list-artifact` atom.
; It repeats this every hour.
; The `process-stream` waits for this to have a value before starting first time.
(def agent-definition
  {:agent-name "twitter-agent"
   :version version
   :schedule [{:name "report-queue-sizes"
              :fun report-queue-sizes
              :seconds 10
              :required-artifacts []}
              {:name "update-rules"
              :fun update-rules
              :seconds (* 60 60)
              :required-artifacts ["domain-list" "doi-prefix-list"]}]
   :runners [
             {:name "ingest-stream"
              :fun ingest-stream
              :required-artifacts []}
             {:name "process-stream"
              :fun process-stream
              :threads 100
              :required-artifacts []}
             ]
   :build-evidence (fn [input] nil)
   :process-evidence (fn [evidence] nil)})

(defn -main [& args]
  (c/run args agent-definition))