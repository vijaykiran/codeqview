;;   Copyright (c) Metadata Partners, LLC and Contributors. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.
(ns datomic.codeq.core
  (:require [clojure.java.io :as io]
            [clojure.set]
            [clojure.string :as string]
            [datomic.api :as d]
            [datomic.codeq.analyzer :as az]
            [datomic.codeq.analyzers.clj]
            [datomic.codeq.util :refer [index->id-fn tempid?]]
            [me.raynes.fs :as fs])
  (:import java.util.Date)
  (:gen-class))

(set! *warn-on-reflection* true)
(def schema
  [;;tx attrs
   {:db/id #db/id [:db.part/db]
    :db/ident :tx/commit
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Associate tx with this git commit"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :tx/file
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Associate tx with this git blob"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :tx/analyzer
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Associate tx with this analyzer"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :tx/analyzerRev
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Associate tx with this analyzer revision"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :tx/op
    :db/valueType :db.type/keyword
    :db/index true
    :db/cardinality :db.cardinality/one
    :db/doc "Associate tx with this operation - one of :import, :analyze"
    :db.install/_attribute :db.part/db}

      ;;git stuff
   {:db/id #db/id [:db.part/db]
    :db/ident :git/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Type enum for git objects - one of :commit, :tree, :blob, :tag"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :git/sha
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "A git sha, should be in repo"
    :db/unique :db.unique/identity
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :repo/commits
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "Associate repo with these git commits"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :repo/uri
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "A git repo uri"
    :db/unique :db.unique/identity
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :commit/parents
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "Parents of a commit"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :commit/tree
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Root node of a commit"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :commit/message
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "A commit message"
    :db/fulltext true
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :commit/author
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Person who authored a commit"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :commit/authoredAt
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "Timestamp of authorship of commit"
    :db/index true
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :commit/committer
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Person who committed a commit"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :commit/committedAt
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "Timestamp of commit"
    :db/index true
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :tree/nodes
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "Nodes of a git tree"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :node/filename
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "filename of a tree node"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :node/paths
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc "paths of a tree node"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :node/object
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Git object (tree/blob) in a tree node"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :git/prior
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Node containing prior value of a git object"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :email/address
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "An email address"
    :db/unique :db.unique/identity
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :file/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "A filename"
    :db/fulltext true
    :db/unique :db.unique/identity
    :db.install/_attribute :db.part/db}

      ;;codeq stuff
   {:db/id #db/id [:db.part/db]
    :db/ident :codeq/file
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Git file containing codeq"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :codeq/loc
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Location of codeq in file. A location string in format \"line col endline endcol\", one-based"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :codeq/parent
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Parent (containing) codeq of codeq (if one)"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :codeq/code
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Code entity of codeq"
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :code/sha
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "SHA of whitespace-minified code segment text: consecutive ws becomes a single space, then trim. ws-sensitive langs don't minify."
    :db/unique :db.unique/identity
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :code/text
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The source code for a code segment"
       ;;:db/fulltext true
    :db.install/_attribute :db.part/db}

   {:db/id #db/id [:db.part/db]
    :db/ident :code/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "A globally-namespaced programming language identifier"
    :db/fulltext true
    :db/unique :db.unique/identity
    :db.install/_attribute :db.part/db}])

(defn ^java.io.Reader exec-stream
  [^String cmd]
  (-> (Runtime/getRuntime)
      (.exec cmd)
      .getInputStream
      io/reader))

(defn ensure-schema [conn]
  (or (-> conn d/db (d/entid :tx/commit))
      (d/transact conn schema)))

(defn list-dir
  "Returns [[sha :type filename] ...]"
  [tree]
  (with-open [s (exec-stream (str "git --git-dir cat-file -p " tree))]
    (let [es (line-seq s)]
      (mapv #(let [ss (string/split ^String % #"\s")]
               [(nth ss 2)
                (keyword (nth ss 1))
                (subs % (inc (.indexOf ^String % "\t")) (count %))])
            es))))

(defn commit
  [git-dir [sha _]]
  (let [trim-email (fn [s] (subs s 1 (dec (count s))))
        dt (fn [ds] (Date. (* 1000 (Integer/parseInt ds))))
        [tree parents author committer msg]
        (with-open [s (exec-stream (str "git --git-dir " git-dir " cat-file -p " sha))]
          (let [lines (line-seq s)
                slines (mapv #(string/split % #"\s") lines)
                tree (-> slines (nth 0) (nth 1))
                [plines xs] (split-with #(= (nth % 0) "parent") (rest slines))]
            [tree
             (seq (map second plines))
             (vec (reverse (first xs)))
             (vec (reverse (second xs)))
             (->> lines
                  (drop-while #(not= % ""))
                  rest
                  (interpose "\n")
                  (apply str))]))]
    {:sha sha
     :msg msg
     :tree tree
     :parents parents
     :author (trim-email (author 2))
     :authored (dt (author 1))
     :committer (trim-email (committer 2))
     :committed (dt (committer 1))}))

(defn commit-tx-data
  [db repo repo-name {:keys [sha msg tree parents author authored committer committed] :as commit}]
  (let [tempid? map? ;;todo - better pred
        sha->id (index->id-fn db :git/sha)
        email->id (index->id-fn db :email/address)
        filename->id (index->id-fn db :file/name)
        authorid (email->id author)
        committerid (email->id committer)
        cid (d/tempid :db.part/user)
        tx-data (fn f [inpath [sha type filename]]
                  (let [path (str inpath filename)
                        id (sha->id sha)
                        filenameid (filename->id filename)
                        pathid (filename->id path)
                        nodeid (or (and (not (tempid? id))
                                        (not (tempid? filenameid))
                                        (ffirst (d/q '[:find ?e :in $ ?filename ?id
                                                       :where [?e :node/filename ?filename] [?e :node/object ?id]]
                                                     db filenameid id)))
                                   (d/tempid :db.part/user))
                        newpath (or (tempid? pathid) (tempid? nodeid)
                                    (not (ffirst (d/q '[:find ?node :in $ ?path
                                                        :where [?node :node/paths ?path]]
                                                      db pathid))))
                        data (cond-> []
                               (tempid? filenameid) (conj [:db/add filenameid :file/name filename])
                               (tempid? pathid) (conj [:db/add pathid :file/name path])
                               (tempid? nodeid) (conj {:db/id nodeid :node/filename filenameid :node/object id})
                               newpath (conj [:db/add nodeid :node/paths pathid])
                               (tempid? id) (conj {:db/id id :git/sha sha :git/type type}))
                        data (if (and newpath (= type :tree))
                               (let [es (list-dir sha)]
                                 (reduce (fn [data child]
                                           (let [[cid cdata] (f (str path "/") child)
                                                 data (into data cdata)]
                                             (cond-> data
                                               (tempid? id) (conj [:db/add id :tree/nodes cid]))))
                                         data es))
                               data)]
                    [nodeid data]))
        [treeid treedata] (tx-data nil [tree :tree repo-name])
        tx (into treedata
                 [[:db/add repo :repo/commits cid]
                  {:db/id (d/tempid :db.part/tx)
                   :tx/commit cid
                   :tx/op :import}
                  (cond-> {:db/id cid
                           :git/type :commit
                           :commit/tree treeid
                           :git/sha sha
                           :commit/author authorid
                           :commit/authoredAt authored
                           :commit/committer committerid
                           :commit/committedAt committed}
                    msg (assoc :commit/message msg)
                    parents (assoc :commit/parents
                                   (mapv (fn [p]
                                           (let [id (sha->id p)]
                                             (assert (not (tempid? id))
                                                     (str "Parent " p " not previously imported"))
                                             id))
                                         parents)))])
        tx (cond-> tx
             (tempid? authorid)
             (conj [:db/add authorid :email/address author])

             (and (not= committer author) (tempid? committerid))
             (conj [:db/add committerid :email/address committer]))]
    tx))

(defn commits
  [git-dir]
  (let [commits (with-open
                 [s (exec-stream
                     (str "git --git-dir " git-dir " log --pretty=oneline --date-order --reverse"))]
                  (mapv
                   #(vector (subs % 0 40)
                            (subs % 41 (count %)))
                   (line-seq s)))]
    commits))

(defn unimported-commits
  [db git-dir]
  (let [imported (into {}
                       (d/q '[:find ?sha ?e
                              :where
                              [?tx :tx/op :import]
                              [?tx :tx/commit ?e]
                              [?e :git/sha ?sha]]
                            db))]
    (pmap #(commit git-dir %) (remove (fn [[sha _]] (imported sha)) (commits git-dir)))))

(defn ensure-db [db-uri]
  (let [newdb? (d/create-database db-uri)
        conn (d/connect db-uri)]
    (ensure-schema conn)
    conn))

(defn import-git
  [conn repo-uri repo-name commits]
  ;;todo - add already existing commits to new repo if it includes them
  (println "Importing repo:" repo-uri "as:" repo-name)
  (let [db (d/db conn)
        repo
        (or (ffirst (d/q '[:find ?e :in $ ?uri :where [?e :repo/uri ?uri]] db repo-uri))
            (let [temp (d/tempid :db.part/user)
                  tx-ret @(d/transact conn [[:db/add temp :repo/uri repo-uri]])
                  repo (d/resolve-tempid (d/db conn) (:tempids tx-ret) temp)]
              (println "Adding repo" repo-uri)
              repo))]
    (doseq [commit commits]
      (let [db (d/db conn)]
        (println "Importing commit:" (:sha commit))
        (d/transact conn (commit-tx-data db repo repo-name commit))))
    (d/request-index conn)
    (println "Import complete!")))

(def analyzers [(datomic.codeq.analyzers.clj/impl)])

(defn run-analyzers
  [git-dir conn]
  (println (str "Analyzing " git-dir " ..."))
  (doseq [a analyzers]
    (let [aname (az/keyname a)
          exts (az/extensions a)
          srevs (set (map first (d/q '[:find ?rev :in $ ?a :where
                                       [?tx :tx/op :schema]
                                       [?tx :tx/analyzer ?a]
                                       [?tx :tx/analyzerRev ?rev]]
                                     (d/db conn) aname)))]
      (println "Running analyzer:" aname "on" exts)
      ;;install schema(s) if not yet present
      (doseq [[rev aschema] (az/schemas a)]
        (when-not (srevs rev)
          (d/transact conn
                      (conj aschema {:db/id (d/tempid :db.part/tx)
                                     :tx/op :schema
                                     :tx/analyzer aname
                                     :tx/analyzerRev rev}))))
      (let [db (d/db conn)
            arev (az/revision a)
            ;;candidate files
            cfiles (set (map first (d/q '[:find ?f :in $ [?ext ...] :where
                                          [?fn :file/name ?n]
                                          [(.endsWith ^String ?n ?ext)]
                                          [?node :node/filename ?fn]
                                          [?node :node/object ?f]]
                                        db exts)))
            ;;already analyzed files
            afiles (set (map first (d/q '[:find ?f :in $ ?a ?rev :where
                                          [?tx :tx/op :analyze]
                                          [?tx :tx/analyzer ?a]
                                          [?tx :tx/analyzerRev ?rev]
                                          [?tx :tx/file ?f]]
                                        db aname arev)))]
        ;;find files not yet analyzed
        (doseq [f (sort (clojure.set/difference cfiles afiles))]
          ;;analyze them
          (println "analyzing file:" f " - sha: " (:git/sha (d/entity db f)))
          (let [db (d/db conn)
                src (with-open [s (exec-stream (str "git --git-dir " git-dir " cat-file -p " (:git/sha (d/entity db f))))]
                      (slurp s))
                adata (try
                        (az/analyze a db f src)
                        (catch Exception ex
                          (println (.getMessage ex))
                          []))]
            (d/transact conn
                        (conj adata {:db/id (d/tempid :db.part/tx)
                                     :tx/op :analyze
                                     :tx/file f
                                     :tx/analyzer aname
                                     :tx/analyzerRev arev})))))))
  (println "Analysis complete!"))

(defn import-data [& [db-uri repo-uri repo-name]]
  (if db-uri
    (let [conn (ensure-db db-uri)
          tmp-repo-folder (fs/temp-dir repo-name)]
      (println "cloning into" tmp-repo-folder)
      (clojure.java.shell/sh "git" "clone" repo-uri (str tmp-repo-folder))
      (import-git conn repo-uri repo-name (unimported-commits (d/db conn) (str tmp-repo-folder "/.git")))
      (run-analyzers (str tmp-repo-folder "/.git") conn))
    (println "Usage: datomic.codeq.core db-uri [commit-name]")))

;; (defn -main
;;   [& args]
;;   (apply main args)
;;   (shutdown-agents)
;;   (System/exit 0))


;;(import-data "datomic:free://localhost:4334/codeq" "https://github.com/Datomic/codeq.git" "codeq")
