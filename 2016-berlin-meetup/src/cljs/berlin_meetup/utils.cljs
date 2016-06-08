(ns berlin-meetup.utils
  (:require [cognitect.transit :as t]
            [om.transit :as omt])
  (:import [goog.net XhrIo]))

(defn transit-post [url]
  (fn [{:keys [remote]} cb]
    (.send XhrIo url
      (fn [e]
        (this-as this
          (cb (t/read (omt/reader) (.getResponseText this)))))
      "POST" (t/write (omt/writer) remote)
      #js {"Content-Type" "application/transit+json"})))
