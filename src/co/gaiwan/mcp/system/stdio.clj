(ns co.gaiwan.mcp.system.stdio
  (:require
   [cheshire.core :as json]
   [co.gaiwan.mcp.protocol :as mcp]
   [co.gaiwan.mcp.state :as state]
   [clojure.tools.logging :as log]))

(defn start! []
  (swap! state/state assoc-in [:sessions :stdio :connections :default]
         {:emit #(do
                   (log/debug :emit %)
                   (println
                    (json/generate-string (:data %))))
          :close (fn [])})
  (log/info :stdio/starting {})
  (future
    (try
      (loop [line (read-line)]
        (when line
          (let [rpc-req (json/parse-string line true)]
            (log/debug :jsonrpc rpc-req)
            (try
              (if (:id rpc-req)
                (mcp/handle-request (assoc rpc-req :state state/state :session-id :stdio :connection-id :default))
                (mcp/handle-notification (assoc rpc-req :state state/state :session-id :default)))
              (catch Throwable e
                (log/error :handler/failed {} :exception e))))
          (recur (read-line))))
      (catch Throwable e
        (log/error :loop/broke {} :exception e)))))
