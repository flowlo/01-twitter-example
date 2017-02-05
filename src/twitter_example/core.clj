(ns twitter-example.core
 (:require [clojure.set]
           [clojure.string]
           [clj-http.client :as client]
           [overtone.at-at :as overtone]
           [twitter.api.restful :as twitter]
           [twitter.oauth :as twitter-oauth]
           [environ.core :refer [env]]
           [clojure.java.io :as io]))

(defn word-chain [word-transition]
  (reduce (fn [r t] (merge-with clojure.set/union r
                                (let [[a b c] t]
                                  {[a b] (if c #{c} #{})})))
          {}
          word-transition))

(defn text->word-chain [s]
  (let [words (clojure.string/split s #"[\s|\n]")
        word-transitions (partition-all 3 1 words)]
    (word-chain word-transitions)))

(defn chain->text [chain]
  (apply str (interpose " " chain)))

(defn end-at-last-punctuation [text]
  (let [trimmed-to-last-punct (apply str (re-seq #"[\s\w]+[^.!?,]*[.!?,]" text))
        trimmed-to-last-word (apply str (re-seq #".*[^a-zA-Z]+" text))
        result-text (if (empty? trimmed-to-last-punct)
                      trimmed-to-last-word
                      trimmed-to-last-punct)
        cleaned-text (clojure.string/replace result-text #"[,| ]$" ".")]
    (clojure.string/replace cleaned-text #"\"" "'")))

(defn walk-chain [prefix chain result maximum]
  (let [suffixes (get chain prefix)]
    (if (empty? suffixes)
     result
     (let [suffix (first (shuffle suffixes))
           new-prefix [(last prefix) suffix]
           result-with-spaces (chain->text result)
           result-char-count (count result-with-spaces)
           suffix-char-count (+ 1 (count suffix))
           new-result-char-count (+ result-char-count suffix-char-count)]
      (if (>= new-result-char-count maximum)
       result
       (recur new-prefix chain (conj result suffix) maximum))))))

(defn generate-text
  [start-phrase word-chain hashtag]
  (let [prefix (clojure.string/split start-phrase #" ")
        result-chain (walk-chain prefix word-chain prefix (- 140 (+ 1 (count hashtag))))
        result-text (chain->text result-chain)]
   (str (end-at-last-punctuation result-text) " " hashtag)))

(defn process-file [fname]
  (text->word-chain
    (slurp (clojure.java.io/resource fname))))

(def files ["1984.txt"])
(def functional-leary (apply merge-with clojure.set/union (map process-file files)))

(def hashtags ["#dystopia" "#orwellian" "#future" "#surveillance" "#bigbrother" "#oneparty" "#warispeace" "#freedomisslavery" "#ignoranceisstrength" "#newspeak" "#oceania"])

(def prefix-list ["I would" "Big Brother" "It was" "There was" "They talked" "She was" "Newspeak is" "The Party" "Winston turned" "What seemed" "The door" "The man" "He paused" "The word" "Abruptly he" "He laid" "At this" "And he" "He raised"])

(defn generate-tweet-text []
  (generate-text (-> prefix-list shuffle first) functional-leary (-> hashtags shuffle first)))

(def my-creds (twitter-oauth/make-oauth-creds (env :app-consumer-key)
                                              (env :app-consumer-secret)
                                              (env :user-access-token)
                                              (env :user-access-secret)))

(defn status-update []
  (let [tweet (generate-tweet-text)]
    (println "generate tweet is :" tweet)
    (println "char count is:" (count tweet))
    (when (not-empty tweet)
      (try (twitter/statuses-update :oauth-creds my-creds
                                  :params {:status tweet})
           (catch Exception e (println "Oh no! " (.getMessage e)))))))

(def my-pool (overtone/mk-pool))

(defn -main [& args]
  ;; every 2 hours
  (println "Started up")
  (overtone/every (* 1000 60 60 2) #(println (status-update)) my-pool))
