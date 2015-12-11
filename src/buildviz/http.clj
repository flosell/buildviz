(ns buildviz.http
  (:require [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [ring.util
             [response :as resp]
             [time :as time]]
            [wharf.core :as wharf]))

(defn respond-with-json [content]
  {:body (wharf/transform-keys (comp wharf/hyphen->lower-camel name) content)})

(defn respond-with-csv [content]
  {:body content
   :headers {"Content-Type" "text/csv;charset=UTF-8"}})

(defn respond-with-xml [content]
  {:body content
   :headers {"Content-Type" "application/xml;charset=UTF-8"}})


(defn wrap-log-request [handler]
  (fn [req]
    (let [resp (handler req)
          method (.toUpperCase (name (:request-method req)))
          uri (:uri req)
          status (:status resp)]
      (log/info (format "\"%s %s\" %s" method uri status))
      resp)))

(defn wrap-log-errors [handler]
  (fn [req]
    (let [resp (handler req)
          status (:status resp)
          body (:body resp)
          method (.toUpperCase (name (:request-method req)))
          uri (:uri req)]
      (when (and (some? status) (>= status 400))
        (log/warn (format "Returned %s for %s %s: \"%s\"" status method uri body)))
      resp)))


(defn- ^java.util.Date date-header [response header]
  (if-let [http-date (resp/get-header response header)]
    (time/parse-date http-date)))

(defn- lose-millis-precision [date]
  (t/minus date (t/millis (t/milli date))))

(defn- not-modified-since? [request modified-date]
  (let [modified-since (date-header request "if-modified-since")
        last-modified (.toDate (lose-millis-precision modified-date))]
    (and modified-since
         (not (.before modified-since last-modified)))))

(defn- resolve-handler-if-modified [req handler modified-date]
  (if (not-modified-since? req modified-date)
    (-> (resp/response nil)
        (resp/status 304))
    (handler req)))

(defn not-modified-request [handler last-modified request]
  (-> request
      (resolve-handler-if-modified handler last-modified)
      (resp/header "Last-Modified" (time/format-date (.toDate last-modified)))))
