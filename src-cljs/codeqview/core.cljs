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

(defn add-page []
  (let [val (atom "")]
    (fn []
      [:div.container
       [:div.row
        [:div.col-md-6.col-md-offset-3
         [:h3 "Add Repository"]
         [:div.panel.panel-default
          [:div.panel-body
           "Wait for a few seconds after adding a repository - you will be redirected to home after loading is done."]]
         [:div.input-group.input-group-md
          [:span.input-group-addon "https://github.com/"]
          [:input.form-control {:type "text"
                                :value @val
                                :placeholder "Datomic/codeq.git"
                                :on-change #(reset! val (-> % .-target .-value))}]
          [:span.input-group-btn
           [:button.btn.btn-success {:type "button"
                                     :on-click #(add-repo (str "https://github.com/" @val))} "Go!"]]]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-10.col-offset-1
     "About CodeqView"]]])

(defn query-page []
  [:div.container
   [:div.row
    [:div.col-md-10.col-offset-1
     "Query !"]]])

(defn home-page []
  [:div.container
   [:div.jumbotron
    [:h1 "Welcome to CodeqView"]
    [:p
     [:a {:href "https://github.com/Datomic/codeq"} "Code Quantum "] "View of your Clojure Code!"]
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
          [:td [:a {:href (str "/repos/" (get repo "eid"))} (get repo "name")]]
          [:td [:span (get repo "url")]]])]]]]
   [:div.row
    [:div.col-md-6.col-md-offset-3.text-center
     [:hr]
     [:span.text-muted "By #_conjurers for "
      [:a {:href "http://clojurecup.com"} "ClojureCup 2015"]
      [:span " _ "]
      [:a {:href "http://twitter.com/vijaykiran"}  "@vijakiran"]
      [:span "•"]
      [:a {:href "http://twitter.com/nehaagarwald"}  "@nehaagarwald"]
      ]]]])

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
