(ns bark.fetch
  (:require [manifold.deferred :as md]
            [bark.http-client :as http]
            [bark.core :as core]
            [bark.json :refer [parse-json]]))

(defn fetch-resource
  [uri]
  (core/retry
    #(md/chain
       (http/get uri {:headers {:accept "application/activity+json"
                                :user-agent (str "Bark " "0.1.0")}})
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
