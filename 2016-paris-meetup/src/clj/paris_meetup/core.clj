(ns paris-meetup.core
  (:require [com.stuartsierra.component :as component]
            [paris-meetup.datomic :as datomic]
            [paris-meetup.server :as server]))

(defn dev-system [config-options]
  (let [{:keys [db-uri web-port]} config-options]
    (component/system-map
      :db (datomic/new-database db-uri)
      :webserver
      (component/using
        (server/dev-server web-port)
        {:datomic-connection :db}))))

(def servlet-system (atom nil))

(def dev-config
  {:db-uri   "datomic:mem://localhost:4334/paris-meetup"
   :web-port 8081})

(defn dev-start []
  (let [sys  (dev-system dev-config)
        sys' (component/start sys)]
    (reset! servlet-system sys')
    sys'))

(defn stop []
  (.stop @servlet-system))

(comment
  (require '[paris-meetup.core :as cc])
  (cc/dev-start)
  (:connection (:db @cc/servlet-system))


  )
