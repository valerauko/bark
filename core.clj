(ns bark.core
  (:require [clojure.tools.logging :as log]
            [clojure.math.numeric-tower :as math]
            [manifold.deferred :as md]
            [manifold.time :as mt]
            [jsonista.core :as json])
  (:import [java.util Date]))

; {:status "failure" ; #{"success" "failure"}
;  :type "send" ; #{"send" "fetch" "inbox"}
;  :activity
;   {:type "Create"
;    :id "https://example.com/activity"}
;  :object
;   {:type "Note"
;    :id "https://example.com/object"}
;  :remote-addr "htts://example.com/inbox"
;  :duration 837.21
;  :error "stacktrace"
;  :attempt 2}

(defn make-logger
  [{:keys [type activity object remote-addr] :as static-fields}]
  (fn logger
    [{:keys [exception attempt retry-in time]}]
    (let [message (json/write-value-as-string
                    (merge static-fields
                           {:status (if exception "failure" "success")
                            :attempt (or attempt 1)
                            :time time}
                           (if (and exception retry-in)
                             {:next-at (Date. ^long (+ (System/currentTimeMillis)
                                                       retry-in))})))]
      (if exception (log/info exception message) (log/info message)))))

(defn millisec-diff
  [start]
  (/ (- (System/nanoTime) start) 1000000.0))

(defn retry
  [f {:keys [max-tries initial-offset stop-on? logger-fn]
      :or {max-tries 5
           initial-offset 10
           stop-on? (constantly nil)
           logger-fn (constantly nil)}}]
  (md/loop [attempt 1]
    (let [start (System/nanoTime)]
      (md/catch
        (md/chain
          (f)
          #(do
             (logger-fn {:time (millisec-diff start)})
             %))
        (fn [error]
          (let [next-attempt (inc attempt)
                retrying? (<= next-attempt max-tries)
                time (millisec-diff start)]
            (if (and retrying? (not (stop-on? error)))
              (let [retry-in (->> next-attempt
                                  (+ (rand 0.3)) ; add jitter
                                  (math/expt 2) ; exponential backoff
                                  (* initial-offset)
                                  (mt/seconds))] ; convert to ms
                (logger-fn {:exception error
                            :attempt attempt
                            :time time
                            :retry-in retry-in})
                (mt/in retry-in #(md/recur next-attempt)))
              (do
                (logger-fn {:exception error
                            :attempt attempt
                            :time time})
                (throw error)))))))))

(defn single-attempt
  [f {:as options}]
  (retry f (assoc options :max-tries 1)))
