(ns codeqview.routes.home
  (:require [codeqview.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [clojure.string :as s]))

(defn home-page []
  (layout/render "home.html"))


(def uri "datomic:free://localhost:4334/codeq")
(def conn (d/connect uri))

(defn get-repos []
  (let [repo-urls (d/q '[:find ?eid ?uri :where [?eid :repo/uri ?uri]]
                       (d/db conn))]
    (mapv (fn [[eid url]]
            {:eid eid
             :name (-> url
                       (s/replace "https://github.com/" "")
                       (s/replace ".git" ""))
             :url url})
          repo-urls)))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/repos" [] (ok (get-repos))))
