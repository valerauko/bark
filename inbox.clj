(ns bark.inbox
  (:require [manifold.deferred :as md]
            [csele.headers :as headers]
            [bark.core :as core]
            [bark.fetch :as fetch]))

(defn check-sig
  "Checks HTTP header signature of the activity's request.
   Refetches the user's public key if it doesn't work at first try.
   Depending on the user's instance that request can be pretty slow."
  [{{{sig-header :signature} :headers
     {actor :actor} :body-params
     :as request} :request
    :keys [find-object]}]
  ; first see if the key in the db (if any) can validate the sig
  (md/let-flow [key (find-object actor)]
    (if (and key (headers/verify request key))
      true
      ; if not then refetch the actor's key and use that to validate
      (md/let-flow [refetched-actor (fetch/fetch-resource actor)
                    refetched-key (:public-key refetched-actor)]
        (and refetched-key (headers/verify request refetched-key))))))

(defn activity-handler
  [{:keys [find-object handlers]
    :as options}]
  (fn [{{{:keys [id type object]
          :as activity} :body} :parameters
        :as request}]
    (md/let-flow [known? (find-object id)
                  signed? (check-sig {:request request
                                      :find-object find-object})]
      (if (and (not known?) signed?)
        (md/let-flow [remote-object (fetch/deref-object object (constantly nil));find-object)
                      handler-fn (get-in handlers [type (:type remote-object)]
                                         (get handlers type (constantly nil)))]
          (handler-fn (assoc-in request [:parameters :body-params :object]
                                        (or remote-object object))))))))

(defn default-logger
  [{{{:keys [id type object]
      :as activity} :body} :parameters
    direct-ip :remote-addr
    {forwarded-ip :X-Forwarded-For} :headers}]
  (core/make-logger
    {:type "inbox"
     :activity (select-keys activity [:id :type])
     :object (if (string? object)
               {:id object}
               (select-keys object [:id :type]))
     :remote-addr (or forwarded-ip direct-ip)}))

(defn inbox-handler
  [options]
  (fn [request]
    (core/retry
      #(md/let-flow [handler (activity-handler options)]
        (handler request))
      (merge {:logger-fn (default-logger request)} options))))
