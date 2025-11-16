(ns co.gaiwan.mcp.system.router
  "HTTP router and middleware setup"
  (:require
   [co.gaiwan.mcp.http-api :as api]
   [co.gaiwan.mcp.lib.ring-sse :as ring-sse]
   [lambdaisland.log4j2 :as log]
   [ruuter.core :as ruuter]))

(defn wrap-mcp-headers [handler]
  (fn [req]
    (let [id (get-in req [:headers "mcp-session-id"])
          version (get-in req [:headers "mcp-protocol-version"])
          res (handler (cond-> req
                         id (assoc :mcp-session-id id)
                         version (assoc :mcp-protocol-version version)))
          id  (or id (get res :mcp-session-id))]
      (if id
        (assoc-in res [:headers "Mcp-Session-Id"] id)
        res))))

(defn wrap-log [handler]
  (fn [req]
    (let [start (System/currentTimeMillis)]
      (log/debug :request/starting (select-keys req
                                                [:request-method :uri :query-string
                                                 :content-type :content-length
                                                 :headers]))
      (let [res (handler req)]
        (log/debug :request/finished {:status (:status res)
                                      :content-type (get-in res [:headers "content-type"])
                                      :content-length (get-in res [:headers "content-length"])
                                      :time-ms (- (System/currentTimeMillis) start)
                                      ;; :body (:body res)
                                      :headers (:headers res)})
        res))))

(defn router []
  (let [routes (into #{(ruuter/get "/ping" [] (constantly {:status 200 :body "pong"}))}
                     (api/routes))]
    (-> (ruuter/routes routes)
        (wrap-log)
        (wrap-mcp-headers)
        (ring-sse/wrap-sse))))
