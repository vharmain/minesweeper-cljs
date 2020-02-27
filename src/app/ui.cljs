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
  {:easy         {:width 9 :height 9 :mines-count 10}
   :intermediate {:width 16 :height 16 :mines-count 40}
   :expert       {:width 30 :height 16 :mines-count 99}})

(def init-state
  {:history []
   :level   :easy
   :game    (game/make-game (:easy levels))})

(defonce state
  (r/atom init-state))

(defn play!
  [game coords]
  (swap! state assoc :game (game/play game coords)))

(defn toggle-flag!
  [game coords]
  (swap! state assoc :game (game/toggle-flag game coords)))

(defn reset-game! [game]
  (swap! state (fn [old-state]
                 (-> old-state
                     (update :history conj game)
                     (assoc :game (game/make-game (levels (:level @state))))))))

(defn select-level!
  [level]
  (swap! state assoc :level level)
  (reset-game! (:game @state)))

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
    :on-click #(reset-game! (:game @state))}
   (condp = (-> @state :game :status)
     :win  "😎"
     :boom "🌚"
     "🌝")])

(defn table
  []
  (let [width  (-> @state :level levels :width)
        height (-> @state :level levels :height)
        border (case (get-in @state [:game :status])
                 :boom "1px solid red"
                 :win  "1px solid green"
                 "1px solid gray")]
    [:table
     {:cellspacing 0
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
                     cell    (-> @state :game (get coords))
                     hidden? (#{:hidden :flagged} (:state cell))]]
           ;; Cell
           [:td
            {:on-click        (fn [evt]
                                (.preventDefault evt)
                                (play! (:game @state) coords))
             :on-context-menu (fn [evt]
                                (.preventDefault evt)
                                (toggle-flag! (:game @state) coords))
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
              :color            (value-colors (:value cell))}}

            ;; Cell content
            (condp = (:state cell)
              :hidden  \u2003 ;; ZWSP
              :flagged [:span {:style {:font-size "0.8em"}} "🚩"]
              (if (= :boom (:value cell))
                [:span {:style {:font-size "0.8em"}} "🙀"]
                (str (:value cell))))]))))]))

(defn game
  []
  [:div
   [:div {:style {:display "flex" :align-items "center"}}
    [level-selector]
    [reset-button]]
   [table]])
