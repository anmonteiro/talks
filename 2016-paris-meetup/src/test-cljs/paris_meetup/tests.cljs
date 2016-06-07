(ns paris-meetup.tests
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [paris-meetup.core :as demo]))

(def gen-tx-share-note
  (gen/tuple
    (gen/vector
      (gen/fmap seq
        (gen/tuple
          (gen/elements '[note/share!])
          (gen/fmap (fn [[note-id user-id]] {:user user-id
                                             :note note-id})
            (gen/tuple
              (gen/elements [0 1])
              (gen/elements [101 102]))))))))

(defn notes-consistent? [{:keys [notes users]}]
  (let [indexed (merge (zipmap (map :id notes) notes)
                       (zipmap (map :id users) users))]
    (letfn [(consistent? [[id {:keys [note/authors user/notes]}]]
              (let [xs (if-not (nil? authors)
                         (map (comp :user/notes indexed :id) authors)
                         (map (comp :note/authors indexed :id) notes))]
                (every? #(some #{id} (map :id %)) xs)))]
      (every? consistent? indexed))))

(defn prop-adds-to-user-notes []
  (prop/for-all* [gen-tx-share-note]
    (fn [[tx ref]]
      (let [parser (om/parser {:read demo/notes-read :mutate demo/notes-mutate})
            state  (atom (om/tree->db demo/NotesApp demo/notes-app-state true))]
        (parser {:state state} tx)
        (let [ui (parser {:state state} (om/get-query demo/NotesApp))]
          (notes-consistent? ui))))))

(defn no-duplicates? [{:keys [notes users]}]
  
  (and (every? #(= (distinct (:note/authors %)) (:note/authors %)) notes)
       (every? #(= (distinct (:user/notes %)) (:user/notes %)) users)))

(defn prop-adds-if-not-present []
  (prop/for-all* [gen-tx-share-note]
    (fn [[tx ref]]
      (let [parser (om/parser {:read demo/notes-read :mutate demo/notes-mutate})
            state  (atom (om/tree->db demo/NotesApp demo/notes-app-state true))]
        (parser {:state state} tx)
        (let [ui (parser {:state state} (om/get-query demo/NotesApp))]
          (no-duplicates? ui))))))

(comment
  (require '[paris-meetup.tests])
  (gen/sample gen-tx-share-note 10)
  (require '[cljs.pprint :as pp :refer [pprint]])
  (tc/quick-check 10 (prop-adds-to-user-notes))
  )
