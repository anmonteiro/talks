(ns paris-meetup.parser
  (:require [datomic.api :as d]
            [om.next.server :as om]))

(defmulti readf om/dispatch)

(defn fetch-people [conn query]
  (d/q '[:find [(pull ?eid query) ...]
         :in $ query
         :where
         [?eid :person/name]]
    (d/db conn)
    query))

(defmethod readf :remote/data
  [{:keys [conn query]} k params]
  (let [result-set (when-not (empty? query)
                     (fetch-people conn query))]
    {:value result-set}))

