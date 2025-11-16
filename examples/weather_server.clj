(ns weather-server
  (:require [co.gaiwan.mcp :as mcp]
            [co.gaiwan.mcp.state :as state]
            [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.client :as http]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HTTP Requests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def NWS_API_BASE "https://api.weather.gov")
(def USER_AGENT "weather-app/1.0")
(def nws-headers {"User-Agent" USER_AGENT
                  "Accept" "application/geo+json"})

(defn get-nws
  [url]
  @(http/get url
             {:headers nws-headers}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Processing API responses
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn forecast->str
  [forecast]
  (str
   (get forecast "name") "\n"
   "Temperature: " (get forecast "temperature") (get forecast "temperatureUnit") "\n"
   "Wind: " (get forecast "windSpeed") "\n"
   "Forecast: " (get forecast "detailedForecast")))

(defn feature->alert
  [feature]
  (let [properties (get feature "properties")]
    (str "Event: " (get properties "event" "Unknown") "\n"
         "Area: " (get properties "areaDesc" "Unknown")  "\n"
         "Severity: " (get properties "severity" "Unknown")  "\n"
         "Description: " (get properties "description" "Unknown")  "\n"
         "Instructions: " (get properties "instruction" "Unknown")  "\n")))

(defn get-alerts
  [state-str]
  (let [url (str NWS_API_BASE "/alerts/active/area/" state-str)
        resp (get-nws url)
        features (-> (:body resp)
                     (json/parse-string true)
                     (get "features"))]
    (map feature->alert features)))

(defn get-forecast
  [lat lon]
  (let [url (str NWS_API_BASE "/points/" lat "," lon)
        forecast-url (-> (get-nws url)
                         (:body)
                         (json/parse-string true)
                         (get-in ["properties" "forecast"]))
        forecast-periods (-> @(http/get forecast-url {:headers nws-headers})
                             (:body)
                             (json/parse-string true)
                             (get-in ["properties" "periods"]))]
    (map forecast->str (take 5 forecast-periods))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MCP
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(state/add-tool
 {:name "weather_alert"
  :title "Weather Alert Tool"
  :description "Given a two letter state code, finds the weather alerts for that state."
  :schema {"type" "object"
           "properties" {"state" {"type" "string"
                                  "description" "Two letter state code"}}
           "required" ["state"]}
  :tool-fn (fn [_req {:keys [state]}]
             (let [alerts (get-alerts state)]
               {:content [{:type :text
                           :text (str/join "\n" (vec alerts))}]
                :isError false}))})

(state/add-tool
 {:name "weather_forecast"
  :title "Weather Forecast Tool"
  :description "Get weather forecast for a location"
  :schema {"type" "object"
           "properties" {"latitude" {"type" "number"
                                     "description" "Latitude of the location"}
                         "longitude" {"type" "number"
                                      "description" "Longitude of the location"}}
           "required" ["latitude" "longitude"]}
  :tool-fn (fn [_req {:keys [latitude longitude]}]
             (let [forecast (get-forecast latitude longitude)]
               {:content [{:type :text
                           :text (str/join "\n" (vec forecast))}]
                :isError false}))})


;; Start MCP
(mcp/run-http! {:port 3999})
