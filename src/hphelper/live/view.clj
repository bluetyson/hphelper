(ns hphelper.live.view
  (:require [hphelper.live.control :as lcon]
            [hphelper.shared.sql :as sql]
            [hphelper.shared.saveload :as sl]
            [hphelper.shared.unique :as uni]
            [hphelper.shared.indicies :as indicies]
            ;; For indicies
            [hphelper.scengen.generator :as sgen]
            [taoensso.timbre :as log]

            [hiccup.core :refer :all]
            )
  (:gen-class)
  )

(defn- html-print-indicies-table
  "Prints indicies in a table in n columns"
  [inds n]
  [:table
   (map (fn [r] [:tr r])
        (partition-all
          n
          (map (fn [x] [:td x])
               (indicies/html-print-indicies inds))))
   ]
  )

(defn view-game
  "Prints a game view nicely for players"
  [baseURL uid]
  (log/trace "view-game. baseURL:" baseURL "uid:" uid)
  (if-let [game (lcon/get-game uid)]
    (html [:html
           [:head
            [:title "Sector indicies"]
            [:meta {:http-equiv "refresh" :content 5}]
            ]
           [:body
            [:div
             (html-print-indicies-table (first (:indicies game)) 13)
             ]
            [:div
             (map (fn [n] [:div n])
                  (:news game))
             ]
            ]
           ])
    (html [:html [:div "Game not found"]])
    ))

(defn- player-key
  "Returns a string with a game's uid and the player's uid for logging in"
  [player]
  (str "Player: " (:name player) ". Player uid: " (:password player) ".")
  )

(defn player-keys
  "Returns a string with all the player's names and their login uids. Nil if incorrect game"
  [^String uid]
  (log/trace "player-keys. uid:" uid "hps:" (:hps (lcon/get-game uid)))
  (if-let [g (lcon/get-game uid)]
    (map (fn [[k pMap]] (player-key pMap)) (:hps g))
    nil))


(defn new-game
  "Creates a new live game by loading a completed scenario. Gives links to player view and GM view"
  [baseURL scenId]
  (if (string? scenId)
    (new-game baseURL (try (Integer/parseInt scenId)
                           (catch Exception e
                             (do (log/trace "new-game. Could not parse:" scenId)
                                 "Invalid scenId"))))
    (let [uid (lcon/new-game (sl/load-fullscen-from-db scenId))]
      (html [:html
             [:body
              [:a {:href (str baseURL "/live/view/" uid "/")} "Player Link"]
              [:br]
              [:a {:href (str baseURL "/live/view/"
                              uid "/"
                              (str (hash uid)) "/")} "GM Link"]
              (map (fn [x] [:div x]) (player-keys uid))
              ]
             ]
            ))))

(defn- create-self-pointing-button
  "Creates a button designed to point to edit-game"
  ([url amount index]
   [:td [:form
         {:action
          (str url
               index "/"
               amount "/")
          :method "get"}
         [:input {:type "submit" :value amount}]
         ]]))

(defn- create-self-pointing-link
  "Creates a link designed to point to edit-game"
  ([url amount index]
   [:td [:a {:href (str url index "/" amount "/")} (str index " " amount)]]))

(defn- create-sorted-indicies-list
  "Creates a list of sorted indicies, sector ones first"
  [inds]
  (let [others (remove (set indicies/sectorIndicies) (keys inds))]
    (concat (sort indicies/sectorIndicies) others)))

(def changeVals "Numbers to give the GM to change sector values by"
  [-10 -8 -5 -3 -1 1 3 5 8 10])

(defn- create-editing-table
  "Creates a html table for modifying sector values"
  [url inds]
  (log/trace "create-editing-table:" url inds)
  (let [ks (map name (create-sorted-indicies-list inds))]
    [:table
     [:tr (map (fn [k] [:td k " " (inds (keyword k))]) ks)]
     (for [r changeVals]
       [:tr (map (partial create-self-pointing-link url r)
                 ks)])
     ]
    )
  )

(defn- single-player-stats
  "Creates a table rown of player stats"
  [{pStats :priStats :as pMap}]
  [:tr
   [:td (:name pMap)]
   [:td (get pStats "Management")]
   [:td (get pStats "Violence")]
   [:td (get pStats "Subterfuge")]
   [:td (get pStats "Hardware")]
   [:td (get pStats "Software")]
   [:td (get pStats "Wetware")]
   [:td (-> pMap :mutation :description)]
   ])


(defn- print-player-stats-table
  "Creates a table of player stats for the GM's pleasure"
  [^String uid]
  [:table {:border 1} [:tr [:td] [:td "M"] [:td "V"] [:td "Su"] [:td "H"] [:td "So"] [:td "W"] [:td "Mutation"]]
   (map single-player-stats (-> (lcon/get-game uid) :hps vals))
   ]
  )

(defn edit-game
  "Prints a view for the GM to edit a game, also performs actions"
  ([baseURL uid confirm]
   (if (= confirm (str (hash uid)))
     (html
       [:html
        [:div
         [:body
          (create-editing-table
            (str baseURL "/live/view/"
                 uid "/"
                 confirm "/")
            (first (:indicies (lcon/get-game uid))))
          [:div [:a {:href (str baseURL "/api/admin/" uid "/" (:adminPass (lcon/get-game uid)) "/debug/")} "Debug Result"]]
          [:div (print-player-stats-table uid)]
          ]]])
     "Incorrect confirmation"
     ))
  ([baseURL uid confirm index amount]
   (if (= confirm (str (hash uid)))
     (do (lcon/modify-index uid (keyword index) amount)
         (edit-game baseURL uid confirm))
     "Incorrect confirmation"))
  )
