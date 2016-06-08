(ns berlin-meetup.parser
  (:require [datomic.api :as d]
            [om.next.server :as om]
            [om.tempid :as omt]))

(defmulti readf om/dispatch)
(defmulti mutatef om/dispatch)

(defn fetch-by-attr [attr conn query]
  (d/q '[:find [(pull ?eid query) ...]
         :in $ query ?attr
         :where
         [?eid ?attr]]
    (d/db conn)
    query
    attr))

(defmethod readf :notes
  [{:keys [conn query]} k params]
  (let [result-set (when-not (empty? query)
                     (fetch-by-attr :note/title conn query))]
    {:value result-set}))

(defmethod readf :users
  [{:keys [conn query]} k params]
  (let [result-set (when-not (empty? query)
                     (fetch-by-attr :user/name conn query))]
    {:value result-set}))

(defmethod mutatef 'app/sync!
  [{:keys [conn]} _ {:keys [users notes] :as params}]
  (let [user-list (into [] (vals users))
        note-list (into [] (vals notes))
        omid->tmpid (atom {})
        tmpid->omid (atom {})
        xf (comp (filter (fn [{:keys [db/id]}]
                           (omt/tempid? id)))
             (map (fn [{:keys [db/id] :as user}]
                    (let [tmpid (d/tempid :db.part/user)]
                      (swap! omid->tmpid assoc id tmpid)
                      (swap! tmpid->omid assoc tmpid id)
                      (assoc user :db/id tmpid)))))
        users' (into [] xf user-list)
        notes' (into [] xf note-list)
        xf-users (map (fn [{:keys [user/notes] :as user}]
                        (update-in user [:user/notes]
                          #(into [] (map (fn [[_ omid]]
                                           {:db/id (get @omid->tmpid omid)})) %))))
        xf-notes (map (fn [{:keys [note/authors] :as note}]
                        (update-in note [:note/authors]
                          #(into [] (map (fn [[_ omid]]
                                           {:db/id (get @omid->tmpid omid)})) %))))
        users' (into [] xf-users users')
        notes' (into [] xf-notes notes')
        tx (into users' notes')
        {:keys [tempids db-after] :as res} @(d/transact conn tx)
        tempids' (into {} (map (fn [[tmpid omid]]
                                 (let [realid (d/resolve-tempid db-after tempids tmpid)
                                       by-id (if (contains? users omid)
                                               :user/by-id
                                               :note/by-id)]
                                   
                                   [[by-id omid] [by-id realid]]))) @tmpid->omid)]
    {:action (fn [] {:tempids tempids'})}))

