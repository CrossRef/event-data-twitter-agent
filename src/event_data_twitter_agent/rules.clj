(ns event-data-twitter-agent.rules
  "Handle Gnip's rules."
  (:require [org.crossref.event-data-agent-framework.core :as c]
            [event-data-common.artifact :as artifact]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [>!!]]
            [clojure.data.json :as json]
            [clj-time.coerce :as clj-time-coerce]
            [clj-time.core :as clj-time]
            [clj-http.client :as client]
            [config.core :refer [env]]
            [event-data-common.status :as status])
  (:import [org.apache.commons.codec.digest DigestUtils])
  (:gen-class))

; http://support.gnip.com/apis/powertrack2.0/rules.html
(def max-rule-length 2048)

(defn- format-gnip-ruleset
  "Format a set of string rules into a JSON object."
  [rule-seq]
  (let [id-base (str (clj-time/now))
        structure {"rules" (map #(hash-map "value" %) rule-seq)}]
    (json/write-str structure)))

(defn- parse-gnip-ruleset
  "Parse the Gnip ruleset into a seq of rule string."
  [json-string]
  (let [structure (json/read-str json-string :key-fn keyword)]
    (map :id (:rules structure))))

(defn- fetch-rule-ids-in-play
  "Fetch the current rule ID set from Gnip."
  []
  (let [fetched (client/get (:gnip-rules-url env) {:basic-auth [(:gnip-username env) (:gnip-password env)]})
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
  (let [result (client/post (:gnip-rules-url env) {:body (format-gnip-ruleset rules) :basic-auth [(:gnip-username env) (:gnip-password env)]})]
    (when-not (#{200 201} (:status result))
      (log/fatal "Failed to add rules" result))))

(defn- remove-rule-ids
  "Add rules to Gnip."
  [rule-ids]
  (let [result (client/post (:gnip-rules-url env) {:body (json/write-str {"rule_ids" rule-ids}) :query-params {"_method" "delete"} :basic-auth [(:gnip-username env) (:gnip-password env)]})]
    (when-not (#{200 201} (:status result))
      (log/fatal "Failed to delete rules" result))))

(defn compact-rules
  [rules]
  "Take a seq of rules, return a tuple of combined rule (up to length) and rest."
  (loop [[rule & rules] (rest rules)
         acc (first rules)
         compacted (list)]
    (if-not rule
      compacted
      (let [new-rule (str acc " OR " rule)]
        (if (> (.length new-rule) max-rule-length)
          ; If appending this one makes it too long, accumulate the compacted ones, and use it as the starter for the next compacted rule.
          (recur rules rule (conj compacted acc))
          (recur rules new-rule compacted))))))


(defn run-update-rules
  "Perform complete update cycle of Gnip rules.
  Do this by fetching the list of domains and prefixes from the 'DOI Destinations' service, creating a rule-set then diffing with what's already in Gnip."
  []
  
  (let [old-rule-ids (fetch-rule-ids-in-play)
        
        domain-list (-> (artifact/fetch-latest-artifact-string "domain-list") (.split "\n") set)
        doi-prefix-list (-> (artifact/fetch-latest-artifact-string "doi-prefix-list") (.split "\n") set)

        doi-prefix-rules (map create-rule-from-prefix doi-prefix-list)
        domain-rules (map create-rule-from-domain domain-list)
        rules (set (concat doi-prefix-rules domain-rules))
        compacted-rules (compact-rules rules)]

    (log/info "Old rules " (count old-rule-ids) ", up to date rules " (count rules))
    (log/info "Artifact provided" (count domain-list) "domains")
    (log/info "Artifact provided" (count doi-prefix-list) "DOI prefixes")

    (log/info "Resulting in " (count rules) "domain rules")
    (log/info "Artifact provided" (count doi-prefix-rules) "DOI prefixe rules")
    (log/info "Total rules:" (count domain-rules))
    (log/info "Total rules compacted:" (count compacted-rules))

    (status/send! "twitter-agent" "rules" "add-rules" (count rules))
    
    (log/info "Add" (count compacted-rules) "new rules")
    (add-rules compacted-rules)
    (log/info "Remove " (count old-rule-ids) "old rules")
    (doseq [chunk (partition-all 1000 old-rule-ids)]
      (log/info "Done chunk of " (count chunk))
      (remove-rule-ids chunk))))
