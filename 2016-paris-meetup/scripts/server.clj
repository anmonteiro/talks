(require '[paris-meetup.core :as cc])

(cc/dev-start)
(println (str "Started server on port " (:web-port cc/dev-config)))

(.addShutdownHook (Runtime/getRuntime)
  (Thread. #(do (cc/stop)
                (println "Server stopped"))))
;; lein trampoline run -m clojure.main scripts/server.clj
