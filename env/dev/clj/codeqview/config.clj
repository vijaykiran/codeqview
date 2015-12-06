(ns codeqview.config
  (:require [selmer.parser :as parser]
            [taoensso.timbre :as timbre]
            [codeqview.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (timbre/info "\n-=[codeqview started successfully using the development profile]=-"))
   :middleware wrap-dev})
