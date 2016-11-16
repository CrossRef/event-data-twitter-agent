(ns event-data-twitter-agent.gnip
  "Handle Gnip's stream."
  (:require [org.crossref.event-data-agent-framework.core :as c]
            [org.crossref.event-data-agent-framework.util :as framework-util])
  (:require [clojure.set :as set]
            [clojure.tools.logging :as l]
            [clojure.core.async :refer [>!!]])
  (:require [org.httpkit.client :as http])
  (:require [clojure.data.json :as json])
  (:import [java.util.concurrent LinkedBlockingQueue]
           [com.twitter.hbc.httpclient.auth BasicAuth]
           [com.twitter.hbc ClientBuilder]
           [com.twitter.hbc.core Constants HttpConstants]
           [com.twitter.hbc.core.processor LineStringProcessor]
           [com.twitter.hbc.core.endpoint RealTimeEnterpriseStreamingEndpoint_v2]
           [org.crossref.eventdata.twitter CustomUrlStreamingEndpoint])
  (:require [clj-time.coerce :as clj-time-coerce])
  (:require [config.core :refer [env]])
  (:gen-class))

(defn- parse-entry
  "Parse a tweet input (JSON String) into a standard format. A single map with keys:
   - tweetId - string id
   - postedTime - string ISO8601 Z directly from twitter
   - postedDate - string ISO8601 Z truncated to day
   - body - body text
   - urls - list of URLs
   - matchingRules - list or Gnip rules that resulted in the match, for diagnostic purposes"
  [input-string]
  (when (.contains input-string "RT") (locking *out* (prn ">>>>>>RT " input-string)))
  (let [parsed (json/read-str input-string)
        posted-time (get-in parsed ["postedTime"])
        urls (map #(get % "expanded_url") (get-in parsed ["gnip" "urls"]))
        matching-rules (map #(get % "value") (get-in parsed ["gnip" "matching_rules"]))]
  {:tweet-url (get parsed "link")
   :author (get-in parsed ["actor" "link"])
   
   :original-tweet-url (get-in parsed ["object" "link"])
   :original-tweet-author (get-in parsed ["object" "actor" "link"])
   
   :posted-time posted-time
   :body (get parsed "body")
   :urls urls
   :matching-rules matching-rules}))

(defn run-stream
  "Run the stream ingestion.
  This pushes events onto two lists:
   - 'input-queue' - a queue for processing
   - 'input-log-YYYY-MM-DD' - the log of inputs. This is written to a log file.
  Blocks forever."
  [event-channel]
  (let [q (new LinkedBlockingQueue 1000) 
        client 
        (-> (new ClientBuilder)
                   (.hosts Constants/ENTERPRISE_STREAM_HOST_v2)
                   (.endpoint (new CustomUrlStreamingEndpoint (:powertrack-endpoint env)))
                   (.authentication (new BasicAuth (:gnip-username env) (:gnip-password env)))
                   (.processor (new com.twitter.hbc.core.processor.LineStringProcessor q))
                   (.build))]
        (l/info "Connecting to Gnip...")
        (.connect client)
        (l/info "Connected to Gnip.")

        (loop []
          ; Block on the take.
          (let [event (.take q)
                parsed (parse-entry event)]

            (c/send-heartbeat "twitter-agent/input/input-stream-event" 1)
            (>!! event-channel parsed))
          (recur))))

(defn- format-gnip-ruleset
  "Format a set of string rules into a JSON object."
  [rule-seq]
  (let [structure {"rules" (map #(hash-map "value" %) rule-seq)}]
    (json/write-str structure)))

(defn- parse-gnip-ruleset
  "Parse the Gnip ruleset into a seq of rule string."
  [json-string]
  (let [structure (json/read-str json-string)
        rules (get structure "rules")]
      (map #(get % "value") rules)))

(defn- fetch-rules-in-play
  "Fetch the current rule set from Gnip."
  []
  (let [fetched @(http/get (:gnip-rules-url env) {:basic-auth [(:gnip-username env) (:gnip-password env)]})
        rules (-> fetched :body parse-gnip-ruleset)]
    (set rules)))

(defn- create-rule-from-domain
  "Create a Gnip rule from a full domain, e.g. www.xyz.com, if valid or nil."
  [full-domain]
  ; Basic sense check.
  (when (> (.length full-domain) 3)
    (str "url_contains:\"//" full-domain "/\"")))

(defn- create-rule-from-prefix
  "Create a Gnip rule from a DOI prefix, e.g. 10.5555"
  [prefix]
  (str "contains:\"" prefix "/\""))

(defn- add-rules
  "Add rules to Gnip."
  [rules]
  (l/info "Post rules" rules)
  (let [result @(http/post (:gnip-rules-url env) {:body (format-gnip-ruleset rules) :basic-auth [(:gnip-username env) (:gnip-password env)]})]
    (when-not (#{200 201} (:status result))
      (l/fatal "Failed to add rules" result))))

(defn- remove-rules
  "Add rules to Gnip."
  [rules]
  (l/info "Remove rules" rules)
  (let [result @(http/delete (:gnip-rules-url env) {:body (format-gnip-ruleset rules) :basic-auth [(:gnip-username env) (:gnip-password env)]})]
    (when-not (#{200 201} (:status result))
      (l/fatal "Failed to delete rules" result))))


(defn update-all-files
  "Perform complete update cycle of Gnip rules.
  Do this by fetching the list of domains and prefixes from the 'DOI Destinations' service, creating a rule-set then diffing with what's already in Gnip."
  [doi-prefix-list-file domain-list-file]
  (let [current-rule-set (fetch-rules-in-play)
        
        doi-prefix-list (framework-util/text-file-to-set doi-prefix-list-file)
        domain-list (framework-util/text-file-to-set domain-list-file)
        doi-prefix-rules (map create-rule-from-prefix doi-prefix-list)
        domain-rules (map create-rule-from-domain domain-list)
        rules (set (concat doi-prefix-rules domain-rules))
        
        rules-to-add (clojure.set/difference rules current-rule-set)
        rules-to-remove (clojure.set/difference current-rule-set rules)]
    (l/info "Current rules " (count current-rule-set) ", up to date rules " (count rules))
    (l/info "Add" (count rules-to-add) ", remove " (count rules-to-remove))

    (c/send-heartbeat "twitter-agent/input/add-rules" (count rules-to-add))
    (c/send-heartbeat "twitter-agent/input/remove-rules" (count rules-to-remove))
    
    (add-rules rules-to-add)
    (remove-rules rules-to-remove)))

