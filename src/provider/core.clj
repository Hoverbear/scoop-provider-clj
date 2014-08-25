(ns provider.core 
  (:use compojure.core)
  (:require [compojure.route       :as route]
            [immutant.web          :as web]
            [immutant.web.servlet  :as servlet]
            [clj-http.client       :as client]
            [cheshire.core         :as chesire]
            [monger.core           :as monger]
            [monger.collection     :as collections]
            [clj-time.format       :as date]
            [clj-time.coerce       :as coerce]
            [monger.json]
            [clojure.walk])
  (:import [com.mongodb MongoOptions ServerAddress])
  (:import org.bson.types.ObjectId)
  (:gen-class))

; MongoDB Details
(def db (monger/get-db 
          (monger/connect {:host "database"})
          "query_composer_development"))

; Gets the session.
(defn get-session
  "Gets the session, get the object with `.getToken` or the access token itself with `.getTokenString`"
  [request]
  (let [{servlet-request :servlet-request} request
        session (.getAttribute servlet-request "org.keycloak.KeycloakSecurityContext")]
    session))

; Visualization List Functions
(defn get-visualization-list
  "Gets the available visualizations"
  []
  ; TODO: Other computations.
  (collections/find-maps db "queries" {} ["description" "title"])) ; Only load description and title.
(defn visualization-list-route
  "Responds with a list of visualizations"
  [request]
  (try
    {:status 200
     :headers {"Content-Type" "application/json; charset=utf-8"}
     :body (chesire/encode (get-visualization-list))}
    (catch Exception e {:status 500 :body (str e)})))

; Specific Visualization Functions
(defn get-result
  "Gets a specific result"
  [id]
  (collections/find-one-as-map db "results" {:execution_id id}))
(defn result-cleaner
  "Cleans a specific result to fit with the expected data returned"
  [result]
  (let [value (dissoc (:value result) :_id :query_id :created_at)]
    {:endpoint (:endpoint_id result)
     :value value}))
(defn get-visualization
  "Gets a single visualization"
  [id]
  (let [db-entry (collections/find-map-by-id db "queries" (ObjectId. id))
        execution-ids (for [execution (:executions db-entry)] (:_id execution))
        executions (filter #(= "complete" (:status %1)) (map get-result execution-ids))
        by-date (group-by #(date/unparse (date/formatter "yyyy-MM-dd") (coerce/from-date (:updated_at %1))) executions)
        cleaned (into {} (map (fn [[k v]] [k (map result-cleaner v)]) by-date))]
    {:_id (:_id db-entry)
     :title (:title db-entry)
     :description (:description db-entry)
     :data cleaned
     :meta {
            :reduce (:reduce db-entry)
            :map (:map db-entry)}}))

(defn visualization-route
  "Responds withs a single visualization"
  [request id]
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body (chesire/encode (get-visualization id))})

; Route declaration.
(defroutes app
  "The router."
  (GET "/api/" [:as request] (visualization-list-route request))
  (GET "/api/:id" [id :as request] (visualization-route request id))
  (route/not-found "<h1>Page not found</h1>"))

(defn -main
  "Start the server"
  [& args]
  ; Start the server.
  (web/run (servlet/create-servlet app) :port 8080))
