(ns fullstackfest-demo.system
  (:require [fullstackfest-demo.datomic :as datomic]
            [fullstackfest-demo.server :as server]
            [environ.core :refer [env]]
            [system.core :refer [defsystem]]
            [system.components.http-kit :refer [new-web-server]]))

(defsystem dev-system
  [:db (datomic/new-database (env :db-uri))
   :webserver (new-web-server (env :web-port) server/app-handler)])
