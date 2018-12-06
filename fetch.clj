(ns bark.fetch
  (:require [manifold.deferred :as md]
            [aleph.http :as http]
            [bark.json :refer [parse-json]]))

(defn fetch-resource
  [uri]
  (md/chain
    (http/get uri {:headers {:accept "application/activity+json"}
                   :connect-timeout 1000
                   :read-timeout 2000})
    :body
    parse-json))
