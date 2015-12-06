(ns codeqview.config
  (:require [taoensso.timbre :as timbre]))

(def defaults
  {:init
   (fn []
     (timbre/info "\n-=[codeqview started successfully]=-"))
   :middleware identity})
