(def +version+ "0.1.0-SNAPSHOT")

(set-env!
  :source-paths    #{"src/clj" "src/cljs" "src/test-cljs"}
  :resource-paths  #{"resources"}
  :dependencies '[ ;; Shared
                  [org.clojure/clojure "1.9.0-alpha11"]
                  [org.omcljs/om               "1.0.0-alpha43"]
                  [bidi                        "2.0.9"          :exclusions [ring/ring-core]]
                  [org.clojure/core.async      "0.2.385"]

                  ;; Client
                  [org.clojure/clojurescript   "1.9.229"]
                  [kibu/pushy "0.3.6"]
                  [com.cognitect/transit-cljs  "0.8.239"]
                  [cljsjs/codemirror "5.8.0-0"]
                  [org.clojure/test.check "0.9.0"]
                  [devcards "0.2.1-7"]

                  ;; Server
                  [com.ladderlife/cellophane   "0.3.5" ]
                  [com.cognitect/transit-clj   "0.8.288"]
                  [org.clojure/tools.logging   "0.3.1"]
                  [org.slf4j/slf4j-log4j12     "1.7.21"]
                  [log4j/log4j                 "1.2.17"
                   :exclusions [javax.mail/mail javax.jms/jms
                                com.sun.jmdk/jmxtools com.sun.jmx/jmxri]]
                  [environ                     "1.1.0"]
                  [ring                        "1.6.0-beta6"]
                  [com.stuartsierra/component "0.3.1"]
                  [com.datomic/datomic-free   "0.9.5394"
                   :exclusions [org.slf4j/slf4j-api org.slf4j/slf4j-nop
                                org.slf4j/slf4j-log4j12 org.slf4j/log4j-over-slf4j]]

                  ;; Tooling
                  [org.danielsz/system         "0.3.2-SNAPSHOT"]
                  [boot-environ "1.1.0"]
                  [com.cemerick/piggieback     "0.2.1"          :scope "test"]
                  [adzerk/boot-cljs            "1.7.228-1"      :scope "test"]
                  [adzerk/boot-cljs-repl       "0.3.0"          :scope "test"]
                  [adzerk/boot-test            "1.1.2"          :scope "test"]
                  [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT" :scope "test"]
                  [adzerk/boot-reload          "0.4.12"         :scope "test"]
                  [adzerk/bootlaces            "0.1.13"         :scope "test"]
                  [org.clojure/tools.nrepl     "0.2.12"         :scope "test"]
                  [org.clojure/tools.namespace "0.3.0-alpha3"   :scope "test"]
                  [weasel                      "0.7.0"          :scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :as cr :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.boot-test :as bt-clj]
 '[adzerk.bootlaces      :refer [bootlaces! push-release]]
 '[clojure.tools.namespace.repl :as repl]
 '[crisptrutski.boot-cljs-test :as bt-cljs]
 '[system.boot :refer [system run]]
 '[environ.boot :refer [environ]]
 '[fullstackfest-demo.system :refer [dev-system]])

(deftask deps [])

(deftask dev []
  (comp
    (environ :env {:db-uri   "datomic:mem://localhost:4334/fullstackfest-demo"
                   :web-port 8081})
    (watch)
    (system :sys #'dev-system :auto true :files ["server.clj" "parser.clj"])
    (cljs-repl)
    (reload :on-jsload 'fullstackfest-demo.core/init!)
    (speak)
    (cljs :source-map true
          :compiler-options {:parallel-build true}
          :ids #{"main"})
    (target)))

(deftask testing []
  (set-env! :source-paths #(conj % "src/test"))
  identity)

(deftask test-clj []
  (comp
    (testing)
    (bt-clj/test)))

(deftask test-cljs
  [e exit?     bool  "Enable flag."]
  (let [exit? (cond-> exit?
                (nil? exit?) not)]
    (comp
      (testing)
      (bt-cljs/test-cljs
        :js-env :node
        :namespaces #{'fullstackfest-demo.tests}
        :cljs-opts {:parallel-build true}
        :exit? exit?))))

(deftask auto-test []
  (comp
    (watch)
    (speak)
    (test-cljs :exit? false)))
