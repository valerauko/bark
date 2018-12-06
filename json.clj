(ns bark.json
  (:require [jsonista.core :as json]))

(defn parse-json
  [input]
  (json/read-value
    input
    (json/object-mapper {:decode-key-fn keyword})))
