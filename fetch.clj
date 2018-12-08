(ns bark.fetch
  (:require [manifold.deferred :as md]
            [bark.http-client :as http]
            [bark.core :as core]
            [bark.json :refer [parse-json]])
  (:import [java.io FileNotFoundException]))

(defn fetch-resource
  [uri]
  (core/retry
    #(md/chain
       (http/get uri {:headers {:accept "application/activity+json"}})
       :body
       parse-json)
    {:logger-fn (core/make-logger {:type "fetch"
                                   :remote-addr uri})
     :stop-on (fn [ex] (or (contains? #{401 403 404 410}
                                      (-> ex ex-data :status))
                           (instance? FileNotFoundException ex)
                           (instance? FileNotFoundException (:cause ex))))}))

(defn deref-object
  [object find-object]
  (if (:id object)
    object
    (md/let-flow [local-object (find-object object)]
      (or local-object (fetch-resource object)))))
