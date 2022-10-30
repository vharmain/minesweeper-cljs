(ns app.ui
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [app.game :as game]
   [goog.object :as go]))


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


(defn get-touch-xy [^js evt]
  (let [touch-list (.-targetTouches evt)]
    (if (pos? (.-length touch-list))
      (let [touch-item (.item touch-list 0)]
        [(.-clientX touch-item) (.-clientY touch-item)])
      [0 0])))


(defn get-mouse-xy [^js evt]
  [(.-clientX evt) (.-clientY evt)])


(defn evt-inside? [^js evt {:keys [x1 y1 x2 y2]}]
  (let [[x y] (if (-> evt .-type (= "touchmove"))
                (get-touch-xy evt)
                (get-mouse-xy evt))]
    (and (<= x1 x x2)
         (<= y1 y y2))))


(def flag-delay 200)
(def timer-evt (doto (js/Object.)
                 (go/set "type" "timer")
                 (go/set "preventDefault" identity)))


(defn client-rect->bounds [^js client-rect]
  {:x1 (.-left client-rect)
   :x2 (.-right client-rect)
   :y1 (.-top client-rect)
   :y2 (.-bottom client-rect)})


(defn- handle-click-event [click-state-atom ^js evt]
  (.preventDefault evt)
  (r/rswap! click-state-atom
            (case (.-type evt)
              ; Start of user interaction. Clear pending timeouts, start new timeout and
              ; save component bounds:
              ("mousedown" "touchstart")
              (fn [{:as click-state :keys [timer]}]
                (when timer
                  (js/clearTimeout timer))
                (assoc click-state
                       :timer (js/setTimeout handle-click-event flag-delay click-state-atom timer-evt)
                       :bounds (-> evt .-target (.getBoundingClientRect) (client-rect->bounds))))

              ; Moving. If we are in user interaction, check if the user is still
              ; within the cell bounds. If not, cancel interaction.
              ("mousemove" "touchmove")
              (fn [{:as click-state :keys [timer bounds]}]
                (if (or (nil? timer) (evt-inside? evt bounds))
                  click-state
                  (do (js/clearTimeout timer)
                      (assoc click-state :timer nil))))

              ; End of user interaction. If we end up here with timer, it means that the
              ; interaction was completed within the same DOM element that it started, *AND* that
              ; interacton ended before the timer was fired. This means it's regular click.
              ("mouseup" "touchend")
              (fn [{:as click-state :keys [timer coords]}]
                (when timer
                  (js/clearTimeout timer)
                  (play! (:game/game @state) coords))
                (assoc click-state :timer nil))

              ; Fired when click has been active for flag-delay milliseconds. This means it's
              ; a "long" click and we need to toggle the flag.
              "timer"
              (fn [{:as click-state :keys [timer coords]}]
                (when timer
                  (js/clearTimeout timer)
                  (toggle-flag! (:game/game @state) coords))
                (assoc click-state :timer nil)))))


(defn prevent-default [^js evt]
  (.preventDefault evt))


(defn- cell [_component coords _border]
  (let [click-state (r/atom {:coords coords})
        click-event-handler (partial handle-click-event click-state)]
    (fn [component coords border]
      (let [cell    (-> @state :game/game :game/board (get coords))
            hidden? (#{:cell.state/hidden :cell.state/flagged} (:cell/state cell))]
        [component
         {:on-touch-start click-event-handler
          :on-touch-move click-event-handler
          :on-touch-end click-event-handler
          :on-mouse-down click-event-handler
          :on-mouse-move click-event-handler
          :on-mouse-up click-event-handler

          :on-click prevent-default
          :on-context-menu prevent-default

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
