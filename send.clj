(ns bark.send
  (:require [bark.http-client :as http]
            [jsonista.core :as json]
            [csele.headers :refer [sign-request]]
            [csele.hash :refer [hash-base64]]
            [bark.core :as core])
  (:import [java.net URI]
           [java.time ZonedDateTime ZoneId]
           [java.time.format DateTimeFormatter]))

(def default-headers
  ["(request-target)" "host" "date" "digest" "content-type"])

(defn header-time
  []
  (let [gmt (ZoneId/of "GMT")
        ; for some reason RFC_1123_DATE_TIME isn't good enough. not sure where
        ; the problem is but mastodon fails to validate the date if the day
        ; number doesn't have a leading zero (and it doesn't in the rfc format)
        formatter (DateTimeFormatter/ofPattern "EEE, dd MMM uuu HH:mm:ss zzz")
        timestamp (ZonedDateTime/now gmt)]
    (.format formatter timestamp)))

(def default-content-type
   (str "application/ld+json;"
        "profile=\"https://www.w3.org/ns/activitystreams\";"
        "charset=utf-8"))

(def default-context
  {(keyword "@context") ["https://www.w3.org/ns/activitystreams"]})

(defn send-activity
  [{:keys [inbox key-map activity headers content-type logger-fn]
    :or {headers default-headers
         content-type default-content-type}
    :as options}]
  (let [logger (or logger-fn
                   (core/make-logger
                     {:type "send"
                      :activity (select-keys activity [:type :id])
                      :object {:type (get-in activity [:object :type])
                               :id (get-in activity [:object :id]
                                           (:object activity))}
                      :remote-addr inbox}))
        edn-body (->> activity (merge default-context))
        body (->> activity (merge default-context) json/write-value-as-bytes)
        target-uri (URI. inbox)
        request-headers {"host" (.getHost target-uri)
                         "date" (header-time)
                         "digest" (str "SHA-256=" (hash-base64 body))
                         "content-type" content-type}
        signature (sign-request {:uri (.getPath target-uri)
                                 :request-method :post
                                 :headers request-headers
                                 :body body}
                                headers
                                key-map)]
    (core/retry #(http/post inbox {:headers (assoc request-headers
                                                   :signature signature)
                                   :body body})
                (assoc options :logger-fn logger))))
