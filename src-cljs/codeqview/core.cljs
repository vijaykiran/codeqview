(ns codeqview.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljs.reader :as reader]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]])
  (:import goog.History))

(def db (atom {:repos []
               :query-results #{}}))

(defn refresh-repos []
  (GET "/repos"
    {:handler #(swap! db assoc :repos %)}))

;;; --- Components

(defn nav-link [uri title page collapsed?]
  [:li {:class (when (= page (session/get :page)) "active")}
   [:a {:href uri
        :on-click #(reset! collapsed? true)}
    title]])

(defn navbar []
  (let [collapsed? (atom true)]
    (fn []
      [:nav.navbar.navbar-default.navbar-fixed-top
       [:div.container
        [:div.navbar-header
         [:button.navbar-toggle
          {:class         (when-not @collapsed? "collapsed")
           :data-toggle   "collapse"
           :aria-expanded @collapsed?
           :aria-controls "navbar"
           :on-click      #(swap! collapsed? not)}
          [:span.sr-only "Toggle Navigation"]
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         [:a.navbar-brand {:href "#/"} "CodeqView"]]
        [:div.navbar-collapse.collapse
         (when-not @collapsed? {:class "in"})
         [:ul.nav.navbar-nav
          [nav-link "#/add"   "Add"   :add collapsed?]
          [nav-link "#/query" "Query" :query collapsed?]
          [nav-link "#/about" "About" :about collapsed?]]
         [:ul.nav.navbar-nav.navbar-right
          [:li {:style {:background-color "orange !important"}}
           [:a {:role "button"
                :style {:color "#fff !important"
                        :font-weight "bold"}
                :href "http://clojurecup.com" :target "_blank"} "Vote for us!"]]]]]])))

(defn repo-page []
  [:div.cotnainer
   [:span "repo Name"]])

(defn add-repo [url]
  (.log js/console url)
  (POST "/repos" {:headers {"Accept" "application/transit+json"
                            "x-csrf-token" (.-value (.getElementById js/document "token"))}
                  :params {:url url}
                  :handler (fn [r]
                             (refresh-repos)
                             (aset js/window "location" "/#"))}))


(defn process-query [qs]
  (.log js/console qs)
  (let [q (reader/read-string qs)]
    (.log js/console q)
    (POST "/query" {:headers {"Accept" "application/transit+json"
                              "x-csrf-token" (.-value (.getElementById js/document "token"))}
                    :params {:query q}
                    :handler (fn [r]
                               (swap! db assoc :query-results r)
                               (.log js/console (str r)))})))


(defn add-page []
  (let [val (atom "")]
    (fn []
      [:div.container
       [:div.row
        [:div.col-md-6.col-md-offset-3
         [:div.panel.panel-info
          [:div.panel-heading "Add a repository"]
          [:div.panel-body
           "Wait for a few seconds after adding a repository - you will be redirected to home after loading is done."]]
         [:div.input-group.input-group-md
          [:span.input-group-addon "https://github.com/"]
          [:input.form-control {:type "text"
                                :value @val
                                :placeholder "vijaykiran/codeqview.git"
                                :on-change #(reset! val (-> % .-target .-value))}]
          [:span.input-group-btn
           [:button.btn.btn-success {:type "button"
                                     :on-click #(add-repo (str "https://github.com/" @val))} "Go!"]]]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-8.col-md-offset-2
     [:div.panel.panel-default
      [:div.panel-heading "What's is this?"]
      [:div.panel-body
       [:p "CodeqView is an interface for "
        [:a {:href "https://github.com/Datomic/codeq" :target "_blank"} "Codeq."]
        [:p "CodeqView's goal is to provide a snappy UI on top of Codeq so you can navigate different Clojure repositories and query them using datalog. When you
add a new repository, it is cloned on the server, the commits are imported into Datomic and finally a all the clj files are analyzed to create the Codeqs of the code."]
        " This application is built using"
        [:ul
         [:li "Clojure"]
         [:li "ClojureScript"]
         [:li "Datomic/Codq"]
         [:li "Reagent"]]]]]]]])

(defn query-results [rs]
  [:div
   [:table.table.table-condensed.table-striped
    [:tbody
     (for [r rs]
       [:tr
        (for [d r]
          [:td (str d)])])]]])

(defn query-page []
  (let [val (atom "")]
    (fn []
      [:div.container
       [:div.row
        [:div.col-md-10.col-offset-1
         [:h1 "Query!"]
         [:div.panel
          [:div.panel-body
           [:a  {:href "https://cloud.github.com/downloads/Datomic/codeq/codeq.pdf" :target "_blank"} "Take a look at Codeq schema"]
           [:span " and explore using datalog. e.g.,:"
            [:ul
             [:li [:code "[:find ?uri :where [_ :repo/uri ?uri]]"]]
             [:li  [:code "[:find (pull ?e [*]) :where  [?e :code/name]]"]]
             [:li  [:code "[:find ?name :where [_ :code/name ?name]]"]]]]]]
         [:div.input-group.input-group-md
          [:span.input-group-addon "Datalog Query:"]
          [:input.form-control {:type "text"
                                :value @val
                                :placeholder "[:find ?name :where [_ :code/name ?name]]"
                                :on-change #(reset! val (-> % .-target .-value))}]
          [:span.input-group-btn
           [:button.btn.btn-primary {:type "button"
                                     :on-click #(process-query @val)} "Go!"]]]]]
       [:hr]
       [:div.row
        [query-results (:query-results @db)]]])))

(defn home-page []
  [:div.container
   [:div.jumbotron
    [:h1 "Welcome to CodeqView"]
    [:p
     [:a {:href "https://github.com/Datomic/codeq" :target "_blank"} "Code Quantums"] " of your Clojure Code!"]
    [:p
     [:a.btn.btn-success.btn-lg {:href "#/add"} "Add a repository"]]]
   [:div.row
    [:div.col-md-12
     [:h2 "Explore Code Quantums!"]
     [:table.table.table-condensed
      [:thead
       [:tr
        [:th "Repository Name"]
        [:th "Github URL"]]]
      [:tbody
       (for [repo (:repos @db)]
         ^{:key (get repo "eid")}
         [:tr
          [:td [:span {:href (str "/repos/" (get repo "eid"))} (get repo "name")]]
          [:td [:span (get repo "url")]]])]]]]
   [:div.row
    [:div.col-md-6.col-md-offset-3.text-center
     [:hr]
     [:span.text-muted "By #_conjurers for "
      [:a {:href "http://clojurecup.com"} "ClojureCup 2015"]
      [:span " _ "]
      [:a {:href "http://twitter.com/vijaykiran"}  "@vijaykiran"]
      [:span "•"]
      [:a {:href "http://twitter.com/nehaagarwald"}  "@nehaagarwald"]]]]])

(def pages
  {:home  #'home-page
   :about #'about-page
   :query #'query-page
   :add   #'add-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/about" []
  (session/put! :page :about))

(secretary/defroute "/add" []
  (session/put! :page :add))

(secretary/defroute "/query" []
  (session/put! :page :query))

(secretary/defroute "/repos/:id" []
  (session/put! :page :repo))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-components []
  (reagent/render [#'navbar] (.getElementById js/document "navbar"))
  (reagent/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-components)
  (refresh-repos))
