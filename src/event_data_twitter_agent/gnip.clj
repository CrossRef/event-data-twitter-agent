(ns event-data-twitter-agent.gnip
  "Handle Gnip's stream."
  (:require [clojure.core.async :refer [go-loop thread buffer chan <!! >!! >! <!]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [>!!]]
            [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clj-time.coerce :as clj-time-coerce]
            [config.core :refer [env]]
            [event-data-common.status :as status])
  (:import [java.util.concurrent LinkedBlockingQueue]
           [com.twitter.hbc.httpclient.auth BasicAuth]
           [com.twitter.hbc ClientBuilder]
           [com.twitter.hbc.core Constants HttpConstants]
           [com.twitter.hbc.core.processor LineStringProcessor]
           [com.twitter.hbc.core.endpoint RealTimeEnterpriseStreamingEndpoint_v2]
           [org.crossref.eventdata.twitter CustomUrlStreamingEndpoint]
           [org.apache.commons.codec.digest DigestUtils])
  (:gen-class))

(def source-id "twitter")
(def source-token "45a1ef76-4f43-4cdc-9ba8-5a6ad01cc231")

(def unknown-url "http://id.eventdata.crossref.org/unknown")

(def version "0.1.9")

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

; Nice big buffer, as they're required for transducers.
(def action-input-buffer 1000000)

; Bunch requests up into chunks of this size.
(def action-chunk-size 100)

; A chan that partitions inputs into large chunks.
(def action-chan (delay (chan action-input-buffer (partition-all action-chunk-size))))

(defn run-ingest
  "Run the stream ingestion.
   Blocks forever."
  [artifacts input-package-channel]
  ; This will send events onto the action-chan, not onto the Agent Framework-provided input-package-channel.
  ; Both args ignored.
  (let [q (new LinkedBlockingQueue 1000)
        c @action-chan
        client 
        (-> (new ClientBuilder)
                   (.hosts Constants/ENTERPRISE_STREAM_HOST_v2)
                   (.endpoint (new CustomUrlStreamingEndpoint (:powertrack-endpoint env)))
                   (.authentication (new BasicAuth (:gnip-username env) (:gnip-password env)))
                   (.processor (new com.twitter.hbc.core.processor.LineStringProcessor q))
                   (.build))]

        (log/info "Connecting to Gnip...")
        (.connect client)
        (log/info "Connected to Gnip.")

        (loop []
            ; Block on the take.
            (let [event (.take q)]
              (try
                ; parsed can return nil if it recognised and 
                (when-let [parsed (parse-entry event)]
                  (>!! c parsed))
                (catch Exception e
                    (log/error (.printStackTrace e))
                    (log/error "Exception parsing Gnip input" event))))
          (recur))))

(defn run-send
  "Take chunks of Actions from the action-chan, assemble into Percolator Input Packages, put them on the input-package-channel.
   Blocks forever."
  [artifacts input-package-channel]
  ; Take chunks of inputs, a few tweets per input bundle.
  ; Gather then into a Page of actions.
  (let [c @action-chan]
    (loop [actions (<!! c)]
      (let [payload {:pages [{:actions actions}]
                     :agent {:version version}
                     :source-token source-token
                     :source-id source-id}]
        (status/send! "twitter-agent" "send" "input-package" (count actions))
        (>!! input-package-channel payload))
      (recur (<!! c)))))
