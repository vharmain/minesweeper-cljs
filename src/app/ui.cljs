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
   :game/game    (game/make-game (:game.level/easy levels))
   :inter/timer  nil})


(defonce state
  (r/atom init-state))


(defn reset-game! []
  (r/rswap! state (fn [{:as old-state :inter/keys [timer] :game/keys [game]}]
                    (when timer
                      (js/clearTimeout timer))
                    (-> old-state
                        (update :game/history conj game)
                        (assoc :game/game (game/make-game
                                           (levels (:game/level old-state))))))))

(defn select-level!
  [level]
  (r/rswap! state assoc :game/level level)
  (reset-game!))


(defn level-selector
  []
  (into [:<>]
        (for [[k _] levels]
          [:button.noselect
           {:style    {:display "inline-block"}
            :on-click #(select-level! k)}
           (name k)])))


(defn reset-button
  []
  [:button.noselect
   {:style
    {:display   "inline-block"
     :font-size "2em"
     :border    "none"
     :cursor    "pointer"}
    :on-click   reset-game!}
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


(defn make-timer-evt []
  (doto (js/Object.)
    (go/set "type" "timer")
    (go/set "preventDefault" identity)))


(defn client-rect->bounds [^js client-rect]
  {:x1 (.-left client-rect)
   :x2 (.-right client-rect)
   :y1 (.-top client-rect)
   :y2 (.-bottom client-rect)})


(defn get-target-coords [^js target]
  (when target
    (let [dataset (-> target .-dataset)]
      (if (-> dataset .-cell (= "true"))
        [(-> dataset .-x (js/parseInt 10)) (-> dataset .-y (js/parseInt 10))]
        (recur (.-parentElement target))))))


(defn- handle-click-event [^js evt]
  (.preventDefault evt)
  (r/rswap! state
            (case (.-type evt)
              ; If this was right mouse click, or left click with either ctrl or meta,
              ; toggle the flag without delay. Otherwise start user interaction. Clear 
              ; possible pending timeout, start new timeout and save component bounds 
              ; and coords to state. Also, save the synthetic timer-evt to state, when 
              ; timer elapses we verify that the event is the same, so that possible 
              ; stray timers don't confuse our state management.
              ("mousedown" "touchstart")
              (fn [{:as state :inter/keys [timer] :game/keys [game]}]
                (js/console.log "e" evt)
                (when timer
                  (js/clearTimeout timer))
                (let [timer-evt (make-timer-evt)
                      coords (-> evt .-target (get-target-coords))]
                  (if (or (-> evt .-ctrlKey)
                          (-> evt .-metaKey)
                          (-> evt .-button (= 2)))
                    (assoc state
                           :game/game (game/toggle-flag game coords)
                           :inter/timer nil)
                    (assoc state
                           :inter/timer (js/setTimeout handle-click-event flag-delay timer-evt)
                           :inter/timer-evt timer-evt
                           :inter/bounds (-> evt .-target (.getBoundingClientRect) (client-rect->bounds))
                           :inter/coords (-> evt .-target (get-target-coords))))))

              ; Moving. If we are in user interaction, check if the user is still
              ; within the cell bounds. If not, cancel the interaction.
              ("mousemove" "touchmove")
              (fn [{:as state :inter/keys [timer bounds]}]
                (if (or (nil? timer) (evt-inside? evt bounds))
                  state
                  (do (js/clearTimeout timer)
                      (assoc state :inter/timer nil))))

              ; End of user interaction. If we end up here with timer, it means that 
              ; the interaction was completed within the same DOM element that it 
              ; started, *AND* that interacton ended before the timer was fired. This 
              ; means it's regular click.
              ("mouseup" "touchend")
              (fn [{:as state :inter/keys [timer coords] :game/keys [game]}]
                (if timer
                  (do (js/clearTimeout timer)
                      (assoc state
                             :game/game (game/play game coords)
                             :inter/timer nil))
                  state))

              ; Fired when click has been active for flag-delay milliseconds. This 
              ; means it's a "long" click and we need to toggle the flag. Verify that
              ; the received event is the one saved in state.
              "timer"
              (fn [{:as state :inter/keys [timer timer-evt coords] :game/keys [game]}]
                (if (and (some? timer) (identical? evt timer-evt))
                  (assoc state
                         :game/game (game/toggle-flag game coords)
                         :inter/timer nil)
                  state)))))


(defn prevent-default [^js evt]
  (.preventDefault evt))


(defn- cell [coords border]
  (let [[x y] coords
        cell    (-> @state :game/game :game/board (get coords))
        hidden? (#{:cell.state/hidden :cell.state/flagged} (:cell/state cell))]
    [:div.noselect
     {:data-x (str x)
      :data-y (str y)
      :data-cell "true"

      :on-touch-start handle-click-event
      :on-touch-move handle-click-event
      :on-touch-end handle-click-event
      :on-mouse-down handle-click-event
      :on-mouse-move handle-click-event
      :on-mouse-up handle-click-event

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
     (for [row  (range height)
           col  (range width)
           :let [coords [col row]]]
       [cell coords border]))))

(defn game
  []
  [:div
   [:div.noselect {:style {:display "flex" :align-items "center"}}
    [level-selector]
    [reset-button]]
   [grid]])
