(defproject berlin-meetup "0.1.0-SNAPSHOT"
  :description "FIXME"
  :url "https://github.com/anmonteiro/talks/berlin-meetup"

  :dependencies [[org.clojure/clojure "1.9.0-alpha4"]
                 [org.clojure/clojurescript "1.9.36"]
                 [org.omcljs/om "1.0.0-alpha36"]
                 [devcards "0.2.1-7"]
                 [figwheel-sidecar "0.5.3-1"]

                 [bidi "2.0.9"]
                 [ring "1.4.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [com.datomic/datomic-free "0.9.5359"]]
  :source-paths ["src/clj" "src/cljs"]
  :clean-targets ^{:protect false} ["target"
                                    "resources/public/main.js"])
