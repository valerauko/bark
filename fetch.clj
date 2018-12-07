(ns bark.fetch
  (:require [manifold.deferred :as md]
            [aleph.http :as http]
            [bark.core :as core]
            [bark.json :refer [parse-json]]))

(defn fetch-resource
  [uri]
  (core/retry
    #(md/chain
       (http/get uri {:headers {:accept "application/activity+json"}
                      :connect-timeout 1000
                      :read-timeout 2000})
       :body
       parse-json)
    {:logger-fn (core/make-logger {:type "fetch"
                                   :remote-addr uri})}))

(defn deref-object
  [object find-object]
  (if (:id object)
    object
    (md/let-flow [local-object (find-object object)]
      (or local-object (fetch-resource object)))))
