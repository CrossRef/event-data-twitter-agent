(ns event-data-twitter-agent.gnip
  "Handle Gnip's stream."
  (:require [clojure.core.async :refer [go-loop thread buffer chan <!! >!! >! <! timeout alts!!]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clj-time.coerce :as clj-time-coerce]
            [config.core :refer [env]]
            [event-data-common.status :as status]
            [clj-http.client :as client]
            [clojure.java.io :as io])
  (:import [org.apache.commons.codec.digest DigestUtils])
  (:gen-class))

(def source-id "twitter")
(def source-token "45a1ef76-4f43-4cdc-9ba8-5a6ad01cc231")

(def unknown-url "http://id.eventdata.crossref.org/unknown")

(def version "0.1.14")

(defn tweet-id-from-url 
  [url]
  (re-find #"[\d]+$" url))

(defn parse-entry
  "Parse a tweet input (JSON String) into an Action.
   On input error log and return nil."
  [input-string]
  (let [parsed (json/read-str input-string :key-fn keyword)]

    (if (:error parsed)
      (do
        (log/error "Gnip error:" (-> parsed :error :message)
        nil))
      (let [posted-time (:postedTime parsed)
        
            ; URL as posted (removing nils).
            expanded-urls (->> parsed :gnip :urls (keep :expanded_url))

            ; URLs as expanded by Gnip (removing nils).
            original-urls (->> parsed :gnip :urls (keep :url))

            urls (set (concat expanded-urls original-urls))

            url (:link parsed unknown-url)

            matching-rules (->> parsed :gnip :matching_rules (keep :id))
            
            plaintext-observations [{:type "plaintext"
                                     :input-content (:body parsed)
                                     :sensitive true}]
            url-observations (map (fn [url]
                                    {:type "url"
                                     :sensitive false
                                     :input-url url}) urls)
        
            title (str "Tweet " (tweet-id-from-url url))]

       {:id (DigestUtils/sha1Hex ^String url)
        :url url
        :occurred-at posted-time
        :extra {:gnip-matching-rules matching-rules}
        :subj {:title title
               :issued posted-time
               :author {:url (-> parsed :actor :link)}
               :original-tweet-url (-> parsed :object :link)
               :original-tweet-author (-> parsed :object :actor :link)}
        :relation-type-id "discusses"
        :observations (concat plaintext-observations
                              url-observations)}))))

(def timeout-duration
  "Time to wait for a new line before timing out. This should be greater than the rate we expect to get tweets. 
   Two minutes should cover the worst case."
  120000)

(defn run
  [c url]
  "Send parsed events to the chan and block.
   On exception, log and exit (allowing it to be restarted)"
  (try
    (let [response (client/get url
                    {:as :stream :basic-auth [(:gnip-username env) (:gnip-password env)]})
          stream (:body response)
          lines (line-seq (io/reader stream))]
        (loop [lines lines]
          (when lines
            (let [timeout-ch (timeout timeout-duration)
                  result-ch (thread (try [(or (first lines) :nil) (rest lines)] (catch java.io.IOException ex (do (log/error "Error getting line from PowerTrack:" (.getMessage ex)) nil))))
                  [[x xs] chosen-ch] (alts!! [timeout-ch result-ch])]

              ; timeout: x is nil, xs is nil
              ; null from server: x is :nil, xs is rest
              ; data from serer: x is data, xs is rest
              (cond
                ; nil from timeout
                (nil? x) (.close stream)
                ; empty string from API, ignore
                (clojure.string/blank? x) (recur xs)
                ; :nil, deliberately returned above
                (= :nil x) (recur xs)
                :default (let [parsed (parse-entry x)]
                           (when parsed
                            (>!! c parsed))
                             (recur xs)))))))
    (catch Exception ex (do
      (log/info (.getMessage ex))))))

(defn run-loop
  [c url]
  (loop [timeout-delay 30000]
    (log/info "Starting / restarting.")
    (run c url)
    (log/info "Stopped")
    (log/info "Try again in" timeout-delay "ms")
    (Thread/sleep timeout-delay)
    (recur timeout-delay)))

; Nice big buffer, as they're required for transducers.
(def action-input-buffer 1000000)

; Bunch requests up into chunks of this size.
(def action-chunk-size 20)

; A chan that partitions inputs into large chunks.
(def action-chan (delay (chan action-input-buffer (partition-all action-chunk-size))))

(defn run-ingest
  "Run the stream ingestion.
   Blocks forever."
  [artifacts input-package-channel]
  ; This will send events onto the action-chan, not onto the Agent Framework-provided input-package-channel.
  ; Both args ignored.
  (let [url (:powertrack-endpoint env)]
    (log/info "Connect to" url)
    (run-loop @action-chan url)))

(defn run-send
  "Take chunks of Actions from the action-chan, assemble into Percolator Input Packages, put them on the input-package-channel.
   Blocks forever."
  [artifacts input-package-channel]
  ; Take chunks of inputs, a few tweets per input bundle.
  ; Gather then into a Page of actions.
  (log/info "Waiting for chunks of actions...")
  (let [c @action-chan]
    (loop [actions (<!! c)]
      (log/info "Got a chunk of" (count actions) "actions")
      (let [payload {:pages [{:actions actions}]
                     :agent {:version version}
                     :source-token source-token
                     :source-id source-id}]
        (status/send! "twitter-agent" "send" "input-package" (count actions))
        (>!! input-package-channel payload)
        (log/info "Sent a chunk of" (count actions) "actions"))
      (recur (<!! c)))))
