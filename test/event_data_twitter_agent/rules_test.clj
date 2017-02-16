(ns event-data-twitter-agent.rules-test
  (:require [clojure.test :refer :all]
            [event-data-twitter-agent.rules :as rules])
  (:import [org.apache.commons.codec.digest DigestUtils]))

(deftest compact-rules
  (testing "compact-rules should include all the input rules, and produce compact rules each no longer than the maximum"
    ; Create a thousand odd unique strings.
    (let [rules (map #(str "-" % "-") (range 1000 120000 99))
          result (rules/compact-rules rules)
          glommed (clojure.string/join rules)]
      (doseq [rule rules]
        (is (<= (.length rule) rules/max-rule-length) "No compacted rule should be longer than stipulated length.")
        (is (.contains glommed rule) "Every rule should be present.")))))
