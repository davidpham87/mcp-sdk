(ns co.gaiwan.mcp.system.http
  "HTTP server component"
  (:require
   [co.gaiwan.mcp.system.router :as router]
   [org.httpkit.server :as http-kit]
   [clojure.tools.logging :as log]))

(defn start! [{:keys [port]
               :or {port 3000}}]
  (log/info "Starting HTTP server on port" port)
  (http-kit/run-server
   (router/router)
   {:port port}))

(defn stop! [server]
  (when server
    (server :timeout 100)))
