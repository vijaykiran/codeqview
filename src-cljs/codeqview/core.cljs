(ns codeqview.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]])
  (:import goog.History))

(def db (atom {:repos []
               :current-repo {}}))

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
          [nav-link "#/about" "About" :about collapsed?]]]]])))

(defn repo-page []
  [:div.cotnainer
   [:span "repo Name"]])

(defn add-page []
  [:div.container
   [:div.row
    [:div.col-md-6
     [:h3 "Add Repository"]
     [:div.input-group.input-group-md
      [:span.input-group-addon "https://github.com/"]
      [:input.form-control {:type "text" :placeholder "Datomic/codeq.git"}]
      [:span.input-group-btn
       [:button.btn.btn-success {:type "button"} "Go!"]]]]]])

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     "About Page"]]])

(defn home-page []
  [:div.container
   [:div.jumbotron
    [:h1 "Welcome to CodeqView" ]
    [:p "Code Quantum View of your Clojure Repository!"]
    [:p [:a.btn.btn-primary.btn-lg {:href "#/add"} "Add a repository"]]]
   [:div.row
    [:div.col-md-12
     [:h2 "Explore Code Quantums!"]
     [:table.table
      [:thead
       [:tr
        [:th "Repository Name"]
        [:th "Github URL"]]]
      [:tbody
       (for [repo (:repos @db)]
         ^{:key (get repo "eid")}
         [:tr
          [:td [:a {:href (str "/repos/" (get repo "eid"))} (get repo "name")]]
          [:td [:span  (get repo "url")]]])]]]]])

(def pages
  {:home  #'home-page
   :about #'about-page
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

(defn get-data []
  (GET "/repos"
      {:handler #(swap! db assoc :repos %)}))

(defn init! []
  (hook-browser-navigation!)
  (mount-components)
  (get-data))
