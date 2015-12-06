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

(def rules
  '[[(node-files ?n ?f) [?n :node/object ?f] [?f :git/type :blob]]
    [(node-files ?n ?f) [?n :node/object ?t] [?t :git/type :tree]
     [?t :tree/nodes ?n2] (node-files ?n2 ?f)]
    [(object-nodes ?o ?n) [?n :node/object ?o]]
    [(object-nodes ?o ?n) [?n2 :node/object ?o] [?t :tree/nodes ?n2] (object-nodes ?t ?n)]
    [(commit-files ?c ?f) [?c :commit/tree ?root] (node-files ?root ?f)]
    [(commit-codeqs ?c ?cq) (commit-files ?c ?f) [?cq :codeq/file ?f]]
    [(file-commits ?f ?c) (object-nodes ?f ?n) [?c :commit/tree ?n]]
    [(codeq-commits ?cq ?c) [?cq :codeq/file ?f] (file-commits ?f ?c)]])

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
