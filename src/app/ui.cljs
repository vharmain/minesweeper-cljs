(ns app.ui
  (:require
   [clojure.string :as str]
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
   (condp = (-> @state :game/game :game/status)
     :game.status/win  "😎"
     :game.status/boom "🌚"
     "🌝")])

(defn- cell
  [component coords border]
  (let [cell    (-> @state :game/game :game/board (get coords))
        hidden? (#{:cell.state/hidden :cell.state/flagged}
                 (:cell/state cell))]
    [component
     {:on-click        (fn [evt]
                         (.preventDefault evt)
                         (play! (:game/game @state) coords))
      :on-context-menu (fn [evt]
                         (.preventDefault evt)
                         (toggle-flag! (:game/game @state) coords))
      :style
      {:display          "grid"
       :align-items      "center"
       :aspect-ratio     "1"
       :cursor           (if hidden? "pointer" "default")
       :box-shadow       (if hidden?
                           #_"2px 2px #ffffff inset, -2px -2px grey inset"
                           ".08em .08em #ffffff inset, -.08em -.08em grey inset"
                           "initial")
       :border           border
       :background-color "#c0c0c0"
       :font-weight      "bold"
       :font-size        "200%"
       :text-align       "center"
       :color            (value-colors (:cell/value cell))}}

     ;; Cell content
     [:div
      {:style
       {:text-align "center"}}

      (condp = (:cell/state cell)
        :cell.state/hidden #_\u2003 ;; ZWSP
        \u2003

        :cell.state/flagged
        [:span {:style {:font-size ".8em"}} "🚩"]

        (if (= :cell.value/boom (:cell/value cell))
          [:span {:style {:font-size ".8em"}} "🙀"]
          (str (:cell/value cell))))]]))

(defn grid []
  (let [width  (-> @state :game/level levels :game/width)
        height (-> @state :game/level levels :game/height)
        border (case (get-in @state [:game/game :game/status])
                 :game.status/boom "1px solid red"
                 :game.status/win  "1px solid green"
                 "1px solid gray")]
    (into
     [:div
      {:style
       {:display               "grid"
        :max-width             (str (* 2.8 width) "em")
        :grid-template-columns (->> "1fr" (repeat width) (str/join " "))}}]
     (for [col  (range width)
           row  (range height)
           :let [coords [col row]]]
       [cell :div coords border]))))

(defn game
  []
  [:div
   [:div {:style {:display "flex" :align-items "center"}}
    [level-selector]
    [reset-button]]
   #_[table]
   [grid]])

(-> @state :game/game :game/board (get [0 0]))
