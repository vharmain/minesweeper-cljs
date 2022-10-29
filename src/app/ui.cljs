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


(defn touch-inside? [^js evt {:keys [x1 y1 x2 y2]}]
  (let [touch-list (.-targetTouches evt)]
    (if (pos? (.-length touch-list))
      (let [touch-item (.item touch-list 0)
            x (.-clientX touch-item)
            y (.-clientY touch-item)]
        (and (<= x1 x x2)
             (<= y1 y y2)))
      false)))


(def flag-delay 200)


(defn- touch-event [touch-state event-type ^js evt]
  (when evt (.preventDefault evt))
  (r/rswap! touch-state
            (case event-type
              :on-timer (fn [{:as tstate :keys [coords]}]
                          (js/console.log "touch: Long!")
                          (toggle-flag! (:game/game @state) coords)
                          (assoc tstate :timer nil))
              :on-touch-start (fn [{:as tstate :keys [timer]}]
                                (js/clearTimeout timer)
                                (let [target (.-target evt)
                                      bounds (.getBoundingClientRect target)]
                                  (assoc tstate
                                         :timer (js/setTimeout (fn [] (touch-event touch-state :on-timer nil)) flag-delay)
                                         :bounds {:x1 (.-left bounds)
                                                  :x2 (.-right bounds)
                                                  :y1 (.-top bounds)
                                                  :y2 (.-bottom bounds)})))
              :on-touch-end (fn [{:as tstate :keys [timer coords]}]
                              (js/clearTimeout timer)
                              (when timer
                                (js/console.log "touch: click")
                                (play! (:game/game @state) coords))
                              (assoc tstate :timer nil))
              :on-touch-move (fn [{:as tstate :keys [timer bounds]}]
                               (if (touch-inside? evt bounds)
                                 tstate
                                 (do (js/clearTimeout timer)
                                     (assoc tstate :timer nil))))
              :on-touch-cancel identity)))


(defn- cell [_component coords _border]
  (let [touch-state (r/atom {:coords coords})
        on-touch-start (partial touch-event touch-state :on-touch-start)
        on-touch-move (partial touch-event touch-state :on-touch-move)
        on-touch-end (partial touch-event touch-state :on-touch-end)
        on-touch-cancel (partial touch-event touch-state :on-touch-cancel)]
    (fn [component coords border]
      (let [cell    (-> @state :game/game :game/board (get coords))
            hidden? (#{:cell.state/hidden :cell.state/flagged}
                     (:cell/state cell))]
        [component
         {:on-touch-start on-touch-start
          :on-touch-move on-touch-move
          :on-touch-end on-touch-end
          :on-touch-cancel on-touch-cancel

          :on-click (fn [evt]
                      (.preventDefault evt)
                      (play! (:game/game @state) coords))

          ; Safari iOS does not generate on-context-menu, handled
          ; by on-touch events:
          :on-context-menu (fn [evt]
                             (.preventDefault evt))

          :style
          {:display             "grid"
           :align-items         "center"
           :aspect-ratio        "1"
           :cursor              (if hidden? "pointer" "default")
           :box-shadow          (if hidden?
                                  ".08em .08em #ffffff inset, -.08em -.08em grey inset"
                                  "initial")
           :border              border
           :outline             0
           :background-color    "#c0c0c0"
           :font-weight         "bold"
           :font-size           "200%"
           :text-align          "center"
           :color               (value-colors (:cell/value cell))}}

       ;; Cell content
         [:div.noselect
          {:style
           {:text-align "center"}}

          (condp = (:cell/state cell)
            :cell.state/hidden
            \u2003 ;ZSWP

            :cell.state/flagged
            [:span {:style {:font-size ".8em"}} "🚩"]

            (if (= :cell.value/boom (:cell/value cell))
              [:span {:style {:font-size ".8em"}} "🙀"]
              (str (:cell/value cell))))]]))))

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
     (for [row  (range height)
           col  (range width)
           :let [coords [col row]]]
       [cell :div coords border]))))

(defn game
  []
  [:div
   [:div {:style {:display "flex" :align-items "center"}}
    [level-selector]
    [reset-button]]
   [grid]])
