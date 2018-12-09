(ns bark.fetch
  (:require [manifold.deferred :as md]
            [bark.http-client :as http]
            [bark.core :as core]
            [bark.json :refer [parse-json]]))

(defn fetch-resource
  [uri]
  (core/retry
    #(md/chain
       (http/get uri {:headers {:accept "application/activity+json"}})
       :body
       parse-json)
    {:logger-fn (core/make-logger {:type "fetch"
                                   :remote-addr uri})
     :stop-if (fn check-if-abort [ex]
                (let [;status (-> ex ex-data :status)] ; aleph throws this
                      status (->> ex ; bark.http-client throws this
                                  Throwable->map
                                  :via
                                  (filter #(= (:type %) 'clojure.lang.ExceptionInfo))
                                  first
                                  :data
                                  :status)]
                  (<= 400 (or status 400) 499)))}))

(defn deref-object
  [object find-object]
  (if (:id object)
    object
    (md/let-flow [local-object (find-object object)]
      (or local-object (fetch-resource object)))))
