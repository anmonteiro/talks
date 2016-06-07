(ns paris-meetup.core
  (:require [paris-meetup.utils :as utils]
            [devcards.core :as dc :include-macros true]
            [devcards.system :as dev]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs.reader :as r]
            [devcards.util.edn-renderer :refer [html-edn]]
            [goog.dom :as gdom]
            [goog.object :as gobj]
            [cljs.pprint :as pp :refer [pprint]]
            [clojure.string :as str]
            [cljsjs.codemirror]
            [cljsjs.codemirror.mode.clojure]
            [cljsjs.codemirror.addons.matchbrackets]
            [cljsjs.codemirror.addons.closebrackets]))

(enable-console-print!)

(defmulti read om/dispatch)
(defmulti mutate om/dispatch)

(defmethod read :remote/data
  [{:keys [query state target]} k params]
  ;; For demo purposes, always throw away what we have in the app state before
  ;; making the remote call
  (when-not (nil? target)
    (swap! state dissoc k))
  {:value (get @state k)
   :remote true})

(defn str->query [query-str]
  (try
    (r/read-string query-str)
    (catch js/Error e "Invalid Query")))

(def cm-opts
  #js {:lineNumbers       true
       :matchBrackets     true
       :autoCloseBrackets true
       :indentWithTabs    false
       :mode              #js {:name "clojure"}})

(defn pprint-src
  "Pretty print src for CodeMirror editor.
  Could be included in textarea->cm"
  [s]
  (-> s
      pprint
      with-out-str))

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

(defn textarea->cm
  "Decorate a textarea with a CodeMirror editor given an id and code as string."
  [id code]
  (let [ta (gdom/getElement id)]
    (js/CodeMirror
      #(.replaceChild (.-parentNode ta) % ta)
      (doto cm-opts
        (gobj/set "value" (str code "\n"))))))

(defn handle-run-query-click! [c]
  (let [cm (om/get-state c :cm)
        query (.getValue cm)]
    (om/set-query! c {:params {:user-query (str->query query)}})))

(defui QueryEditor
  static om/IQueryParams
  (params [this]
    {:user-query [:person/name :person/age :person/address]})
  static om/IQuery
  (query [this]
    '[{:remote/data ?user-query}])
  Object
  (componentDidMount [this]
    (let [query (:user-query (om/get-params this))
          src (pprint-src query)
          cm (textarea->cm "query-editor" src)]
      (om/update-state! this assoc :cm cm)))
  (render [this]
    (let [props (om/props this)
          local (om/get-state this)]
      (dom/div nil ;#js {:style #js {:display "table"}}
        (dom/div #js {:style #js {:display "inline-block"
                                  :verticalAlign "top"
                                  :width "50%"
                                  ;:paddingRight "20px"
                                  }}
          (dom/p nil)
          (dom/div #js {:id "query-editor"})
          (dom/button #js {:style #js {:fontSize "20px"
                                       :marginLeft "20px"}
                           :onClick (partial handle-run-query-click! this)} "Run Query"))
        (dom/div #js {:style #js {:display "inline-block"
                                  :verticalAlign "top"
                                  :width "50%"
                                  ;:borderLeft "black 2px solid"
                                  ;:paddingLeft "20px"
                                  }}
          (devcard "Run query result" "" (:remote/data props) {:heading false}))))))

(def reconciler
  (om/reconciler {:state {}             ;init-state
                  :parser (om/parser {:read read
                                      :mutate mutate})
                  :send (utils/transit-post "/api")}))

;; =============================================================================

(def notes-app-state
  {:notes [{:id 0 :note/title "Some note" :note/content "The note's content"
           :note/authors [{:id 101 :user/name "Alice Brown"}]}
           {:id 1 :note/title "Untitled" :note/content "TODOs this week"
            :note/authors [{:id 101 :user/name "Alice Brown"}]}]
   :users [{:id 101 :user/name "Alice Brown" :user/notes [{:id 0} {:id 1}]}
           {:id 102 :user/name "Bob Atkins" :user/notes []}]})

(declare Note)

(defui Author
  static om/Ident
  (ident [this {:keys [id]}]
    [:user/by-id id])
  static om/IQuery
  (query [this]
    [:id :user/name])
  Object
  (render [this]
    (dom/span nil (-> (om/props this) :user/name))))

(def author (om/factory Author {:keyfn :id}))

(defui User
  static om/Ident
  (ident [this {:keys [id]}]
    [:user/by-id id])
  static om/IQuery
  (query [this]
    [:id :user/name {:user/notes (om/get-query Note)}])
  Object
  (render [this]
    (let [{:keys [user/name user/notes]} (om/props this)]
      (dom/li #js {:style #js {:marginBottom "40px"}}
        (dom/p nil name)
        (dom/h4 #js {:style #js {:marginLeft "15px"
                                 :marginBottom "0px"}} "Notes owned:")
        (dom/ul nil
          (map #(dom/li #js {:key (:id %)} (:note/title %)) notes))))))

(def user (om/factory User {:keyfn :id}))

(defui Note
  static om/Ident
  (ident [this {:keys [id]}]
    [:note/by-id id])
  static om/IQuery
  (query [this]
    [:id :note/title :note/content {:note/authors (om/get-query Author)}])
  Object
  (render [this]
    (let [{:keys [id note/title note/content note/authors]} (om/props this)]
      (dom/div #js {:style #js {:border "1px solid black"
                                :padding "10px"}}
        (dom/h2 #js {:style #js {:marginBottom "10px"}} title)
        (dom/h4 #js {:style #js {:marginTop "10px"}}
          "owned by: "
          (interpose ", " (map author authors)))
        (dom/p nil content)
        (dom/button #js {:onClick #(om/transact! this `[(note/share! {:note ~id
                                                                      :user 102}) :users])}
          "Add Bob to note")))))

(def note (om/factory Note {:keyfn :id}))

(defui NotesApp
  static om/IQuery
  (query [this]
    [{:notes (om/get-query Note)}
     {:users (om/get-query User)}])
  Object
  (render [this]
    (let [{:keys [notes users]} (om/props this)]
      (dom/div #js {:style #js {:display "table"
                                :marginLeft "50px"
                                :fontFamily "Helvetica"}}
        (dom/div #js {:style #js {:width "300px"
                                  :display "table-cell"
                                  :borderRight "black 1px solid"}}
          (dom/h1 nil "Notes")
          (map note notes))
        (dom/div #js {:style #js {:width "350px"
                                  :display "table-cell"
                                  :paddingLeft "30px"}}
          (dom/h1 nil "Users")
          (map user users))))))

(defmulti notes-read om/dispatch)
(defmulti notes-mutate om/dispatch)

(defmethod notes-read :default
  [{:keys [state query]} k _]
  (let [st @state]
    {:value (om/db->tree query (get st k) st)}))

(defn share-note [{:keys [note user]} state]
  (-> state
    (update-in [:note/by-id note :note/authors]
      (fn [authors]
        (cond-> authors
          (not (some #{[:user/by-id user]} authors))
          (conj [:user/by-id user]))))
    (update-in [:user/by-id user :user/notes]
      (fn [notes]
        (cond-> notes
          (not (some #{[:note/by-id note]} notes))
          (conj [:note/by-id note]))))))

(defmethod notes-mutate 'note/share!
  [{:keys [state]} _ params]
  {:action #(swap! state (partial share-note params))})

(def notes-reconciler
  (om/reconciler {:state notes-app-state
                  :parser (om/parser {:read notes-read
                                      :mutate notes-mutate})}))

(defn setup-app! []
  (let [target (gdom/getElement "app")
        location (.. js/window -location -pathname)]
    (case location
      ("/" "/index.html")
      (do (dev/add-css-if-necessary!)
          (om/add-root! reconciler QueryEditor target))

      "/app.html"
      (om/add-root! notes-reconciler NotesApp target))))

(setup-app!)
