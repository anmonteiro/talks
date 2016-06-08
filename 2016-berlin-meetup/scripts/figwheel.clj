(require '[figwheel-sidecar.repl :as r]
         '[figwheel-sidecar.repl-api :as ra])

(ra/start-figwheel!
  {:build-ids ["dev"]
   :all-builds
   [{:id "dev"
     :figwheel true
     :source-paths ["src/cljs"]
     :compiler {:main 'berlin-meetup.core
                :asset-path "/out"
                :output-to "resources/public/main.js"
                :output-dir "resources/public/out"
                :parallel-build true
                :compiler-stats true}}]})

(ra/cljs-repl)
