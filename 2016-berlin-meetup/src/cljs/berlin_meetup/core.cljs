(ns berlin-meetup.core
  (:require [berlin-meetup.utils :as utils]
            [devcards.core :as dc :include-macros true]
            [devcards.system :as dev]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [devcards.util.edn-renderer :refer [html-edn]]
            [goog.dom :as gdom]))

(enable-console-print!)

(defn make-card [card-body]
  (if (satisfies? dc/IDevcardOptions card-body)
    card-body
    (reify dc/IDevcardOptions
      (-devcard-options [this opts]
        (assoc opts :main-obj card-body)))))

(defn devcard [name doc main-obj & [opts]]
  (let [card (cond-> {:name name
                      :documentation doc
                      :main-obj (make-card main-obj)}
               (not (nil? opts)) (merge {:options opts}))]
    (dc/card-base card)))

(declare Note)

(defui Author
  static om/Ident
  (ident [this {:keys [db/id]}]
    [:user/by-id id])
  static om/IQuery
  (query [this]
    [:db/id :user/name])
  Object
  (render [this]
    (dom/span nil (-> (om/props this) :user/name))))

(def author (om/factory Author {:keyfn :db/id}))

(defui User
  static om/Ident
  (ident [this {:keys [db/id]}]
    [:user/by-id id])
  static om/IQuery
  (query [this]
    [:db/id :user/name {:user/notes (om/get-query Note)}])
  Object
  (render [this]
    (let [{:keys [user/name user/notes]} (om/props this)]
      (dom/li #js {:style #js {:marginBottom "40px"}}
        (dom/p nil name)
        (dom/h4 #js {:style #js {:marginLeft "15px"
                                 :marginBottom "0px"}} "Notes owned:")
        (dom/ul nil
          (map #(dom/li #js {:key (:db/id %)} (:note/title %)) notes))))))

(def user (om/factory User {:keyfn :db/id}))

(defui UsersList
  Object
  (render [this]
    (let [{:keys [owner]} (om/get-computed this)]
      (dom/div #js {:style #js {:width "250px"
                                :display "table-cell"
                                :paddingLeft "30px"}}
        (dom/h1 nil "Users")
        (map user (om/props this))
        (dom/div nil
          (dom/button #js {:style #js {:marginTop 15
                                       :height "40px"
                                       :width "100px"
                                       :fontSize "1em"}
                           :onClick #(om/transact! owner '[(user/create!)])}
            "New user"))))))

(def users-list (om/factory UsersList))

(defui Note
  static om/Ident
  (ident [this {:keys [db/id]}]
    [:note/by-id id])
  static om/IQuery
  (query [this]
    [:db/id :note/title :note/content {:note/authors (om/get-query Author)}])
  Object
  (render [this]
    (let [{:keys [db/id note/title note/content note/authors]} (om/props this)]
      (dom/div #js {:style #js {:border "1px solid black"
                                :padding "10px"}}
        (dom/h2 #js {:style #js {:marginBottom "10px"}} title)
        (dom/h4 #js {:style #js {:marginTop "10px"}}
          "owned by: "
          (interpose ", " (map author authors)))
        (dom/textarea #js {:rows 3
                           :value content})
        (dom/div nil
          (dom/button
            #js {:onClick #(om/transact! this `[(note/share! {:note ~id}) :users])}
            "Add António to note"))))))

(def note (om/factory Note {:keyfn :db/id}))

(defui NotesList
  Object
  (render [this]
    (let [{:keys [owner]} (om/get-computed this)]
      (dom/div #js {:style #js {:width "300px"
                                :display "table-cell"
                                :borderRight "black 1px solid"}}
        (dom/h1 nil "Notes")
        (map note (om/props this))
        (dom/div nil
          (dom/button #js {:style #js {:marginTop 15
                                       :height "40px"
                                       :width "100px"
                                       :fontSize "1em"}
                           :onClick #(om/transact! owner '[(note/create!)])}
            "New note"))))))

(def notes-list (om/factory NotesList))

(declare notes-reconciler)

(defui StateInspector
  Object
  (render [this]
    (dom/div #js {:style #js {;:width "300px"
                              :display "table-cell"
                              :borderRight "black 1px solid"}}
      (devcard "Props inspector" "" @notes-reconciler {:heading false}))))

(def state-inspector (om/factory StateInspector))

(defui NotesApp
  static om/IQuery
  (query [this]
    [{:notes (om/get-query Note)}
     {:users (om/get-query User)}])
  Object
  (render [this]
    (let [{:keys [notes users]} (om/props this)]
      (dom/div nil
        (dom/div nil
          (dom/button #js {:style #js {:marginLeft 200
                                       :marginBottom 40
                                       :height 40
                                       :width 150
                                       :fontSize "1.5em"}
                           :onClick #(om/transact! this '[(app/sync!)])}
            "Sync app!"))
        (dom/div #js {:style #js {:display "table"
                                  :marginLeft "50px"
                                  :fontFamily "Helvetica"}}
          
          (notes-list (om/computed notes {:owner this}))
          (users-list (om/computed users {:owner this}))
          (state-inspector (om/props this)))))))

(defmulti notes-read om/dispatch)
(defmulti notes-mutate om/dispatch)

(defmethod notes-read :default
  [{:keys [state query]} k _]
  (let [st @state]
    (if (contains? st k)
      {:value (om/db->tree query (get st k) st)}
      {:remote true})))

(defmethod notes-mutate 'user/create!
  [{:keys [state]} _ _]
  {:action #(let [id (om/tempid)
                  ident [:user/by-id id]]
              (swap! state
                (fn [st]
                  (-> st
                    (update-in ident merge
                      {:db/id id
                       :user/name "António Monteiro"
                       :user/notes []})
                    (update-in [:users] conj ident)))))})

(defmethod notes-mutate 'note/create!
  [{:keys [state]} _ _]
  {:action #(let [id (om/tempid)
                  ident [:note/by-id id]]
              (swap! state
                (fn [st]
                  (-> st
                    (update-in ident merge
                      {:db/id id
                       :note/title "Untitled"
                       :note/content ""
                       :note/authors []})
                    (update-in [:notes] conj ident)))))})

(defn share-note [{:keys [note]} st]
  (let [user (first (filter (fn [[_ id]] (om/tempid? id)) (:users st)))]
    (assert user)
    (-> st
      (update-in [:note/by-id note :note/authors]
        conj user)
      (update-in (conj user :user/notes)
        conj [:note/by-id note]))))

(defmethod notes-mutate 'note/share!
  [{:keys [state]} _ params]
  {:action #(swap! state (partial share-note params))})

(defmethod notes-mutate 'app/sync!
  [{:keys [state ast]} _ params]
  (let [st @state
        params {:users (:user/by-id st)
                :notes (:note/by-id st)}]
    {:remote (assoc-in ast [:params] params)}))

(def notes-reconciler
  (om/reconciler {:state {}
                  :id-key :db/id
                  :send (utils/transit-post "/api")
                  :parser (om/parser {:read notes-read
                                      :mutate notes-mutate})}))

(defn setup-app! []
  (dev/add-css-if-necessary!)
  (om/add-root! notes-reconciler NotesApp (gdom/getElement "app")))

(setup-app!)
