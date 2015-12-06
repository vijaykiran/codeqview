(ns codeqview.routes.home
  (:require [codeqview.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [datomic.codeq.core :as codeq]
            [clojure.string :as s]))

(defn home-page []
  (layout/render "home.html"))


(def uri "datomic:free://localhost:4334/codeq")
(defn conn [] (codeq/ensure-db uri))

(defn get-name [url]
  (-> url
      (s/replace "https://github.com/" "")
      (s/replace ".git" "")))

(defn get-repos []
  (let [repo-urls (d/q '[:find ?eid ?uri :where [?eid :repo/uri ?uri]]
                       (d/db (conn)))]
    (mapv (fn [[eid url]]
            {:eid eid
             :name (get-name url)
             :url url})
          repo-urls)))

(defn import-repo [req]
  (let [repo-url (get (:params req) :url)]
    (println repo-url)
    (codeq/import-data uri repo-url (get-name repo-url))
    (ok (get-repos))))

(defroutes home-routes
  (GET "/" [] (home-page))
  (POST "/repos" [] import-repo)
  (GET "/repos" [] (ok (get-repos))))
