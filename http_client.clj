(ns bark.http-client
  (:refer-clojure :exclude [get])
  (:require [byte-streams :as bs])
  (:import [java.net URL HttpURLConnection]))

(def content-type
   (str "application/ld+json;"
        "profile=\"https://www.w3.org/ns/activitystreams\";"
        "charset=utf-8"))

(defn open-conn
  [uri {:keys [connection-timeout read-timeout headers]
        :or {connection-timeout 1000 read-timeout 5000
             headers {"Accept" content-type}}}]
  (let [url (java.net.URL. uri)
        conn (.openConnection url)]
    (doseq [[header value] headers]
      (.setRequestProperty conn (name header) value))
    (doto conn
          (.setConnectTimeout connection-timeout)
          (.setReadTimeout read-timeout))))

(defn get
  [uri options]
  (future
    (let [conn ^HttpURLConnection (open-conn uri options)
          status (.getResponseCode conn)]
      (try
        {:status status
          :body (bs/to-string (.getInputStream conn))}
        (catch Exception e
          (throw (ex-info "GET request failed."
                          {:status status}
                          (or (:cause e) e))))))))

(defn post
  [uri {:keys [body] :as options}]
  (future
    (let [conn (doto ^HttpURLConnection (open-conn uri options)
                                        (.setRequestMethod "POST")
                                        (.setDoOutput true))]

      (doto (.getOutputStream conn)
            (.write ^bytes body)
            .close)
      (let [status (.getResponseCode conn)]
        (try
          {:status status
            :body (bs/to-string (.getInputStream conn))}
          (catch Exception e
            (throw (ex-info "POST request failed."
                            {:status status}
                            (or (:cause e) e)))))))))
