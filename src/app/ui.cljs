(ns app.ui
  (:require
   [reagent.core :as r]
   [app.game :as game]))

(def value-colors
  {0 "#c0c0c0"
   1 "#0100fe"
   2 "#017f01"
   3 "#fe0000"
   4 "#010080"
   5 "#810102"
   6 "#008081"
   7 "#000000"
   8 "#808080"})

(def levels
  {:game.level/easy         {:game/width 9 :game/height 9 :game/mines-count 10}
   :game.level/intermediate {:game/width 16 :game/height 16 :game/mines-count 40}
   :game.level/expert       {:game/width 30 :game/height 16 :game/mines-count 99}})

(def init-state
  {:game/history []
   :game/level   :game.level/easy
   :game/game    (game/make-game (:game.level/easy levels))})

(defonce state
  (r/atom init-state))

(defn play!
  [game coords]
  (swap! state assoc :game/game (game/play game coords)))

(defn toggle-flag!
  [game coords]
  (swap! state assoc :game/game (game/toggle-flag game coords)))

(defn reset-game! [game]
  (swap! state (fn [old-state]
                 (-> old-state
                     (update :game/history conj game)
                     (assoc :game/game (game/make-game
                                        (levels (:game/level @state))))))))

(defn select-level!
  [level]
  (swap! state assoc :game/level level)
  (reset-game! (:game/game @state)))

(defn level-selector
  []
  (into [:<>]
        (for [[k _] levels]
          [:button
           {:style    {:display "inline-block"}
            :on-click #(select-level! k)}
           (name k)])))

(defn reset-button
  []
  [:button
   {:style
    {:display   "inline-block"
     :font-size "2em"
     :border    "none"
     :cursor    "pointer"}
    :on-click #(reset-game! (:game/game @state))}
   (condp = (-> @state ::game/game :game/status)
     :game.status/win  "😎"
     :game.status/boom "🌚"
     "🌝")])

(defn table
  []
  (let [width  (-> @state :game/level levels :game/width)
        height (-> @state :game/level levels :game/height)
        border (case (get-in @state [:game/game :game/status])
                 :game.status/boom "1px solid red"
                 :game.status/win  "1px solid green"
                 "1px solid gray")]
    [:table
     {:cellSpacing 0
      :styl
      {:border           border
       :background-color "#808080"}}

     (into
      [:tbody]
      (for [row (range height)]
        (into
         [:tr]
         (for [col  (range width)
               :let [coords [col row]
                     cell    (-> @state :game/game (get coords))
                     hidden? (#{:cell.state/hidden :cell.state/flagged}
                              (:cell/state cell))]]
           ;; Cell
           [:td
            {:on-click        (fn [evt]
                                (.preventDefault evt)
                                (play! (:game/game @state) coords))
             :on-context-menu (fn [evt]
                                (.preventDefault evt)
                                (toggle-flag! (:game/game @state) coords))
             :style
             {:width            "1em"
              :height           "1em"
              :cursor           (if hidden? "pointer" "default")
              :box-shadow       (if hidden?
                                  "2px 2px #ffffff inset, -2px -2px grey inset"
                                  "initial")
              :border           border
              :background-color "#c0c0c0"
              :font-weight      "bold"
              :font-size        "2em"
              :text-align       "center"
              :color            (value-colors (:cell/value cell))}}

            ;; Cell content
            (condp = (:cell/state cell)
              :cell.state/hidden  \u2003 ;; ZWSP
              :cell.state/flagged [:span {:style {:font-size "0.8em"}} "🚩"]
              (if (= :cell.value/boom (:cell/value cell))
                [:span {:style {:font-size "0.8em"}} "🙀"]
                (str (:cell/value cell))))]))))]))

(defn game
  []
  [:div
   [:div {:style {:display "flex" :align-items "center"}}
    [level-selector]
    [reset-button]]
   [table]])

(-> @state :game/game :game/status)
