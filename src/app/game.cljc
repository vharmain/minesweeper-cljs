(ns app.game)

(defn gen-mine
  [max-x max-y]
  [(rand-int max-x) (rand-int max-y)])

(defn gen-mines
  "Returns an infinite sequence of generated mines"
  [width height]
  (repeatedly #(gen-mine width height)))

(defn coords-around
  "Returns coordinates that surround given coordinates
   ? ? ?
   ? * ?
   ? ? ?"
  [[x y]]
  (for [xd    (range -1 2)
        yd    (range -1 2)
        :when (not= [xd yd] [0 0])]
    [(+ x xd) (+ y yd)]))

(defn mines-around
  [coords mines]
  (->> (coords-around coords)
       (filter mines)))

(defn calc-value
  [coords mines]
  (if (contains? mines coords)
    :boom
    (count (mines-around coords mines))))

(defn calc-grid
  [width height mines]
  (for [row  (range height)
        col  (range width)
        :let [coords [row col]]]
    {:coords coords
     :state  :hidden
     :value  (calc-value coords mines)}))

(defn make-game
  [{:keys [width height mines-count]}]
  (->> (gen-mines width height)
       distinct
       (take mines-count)
       set
       (calc-grid width height)
       (map (juxt :coords identity))
       (into {:status :ok :mines-count mines-count})))

(defn reveal
  [game coords]
  (update game coords assoc :state :visible))

(defn reveal-around
  [game coords]
  (->> (coords-around coords)
       (filter game)
       (reduce reveal game)))

(defn hidden-zeros-around
  [game coords]
  (->> (coords-around coords)
       (filter (fn [coords]
                 (and
                  (-> coords game :value zero?)
                  (-> coords game :state (= :hidden)))))))

(defn hidden-cells
  [game]
  (->> game
       vals
       (filter (fn [cell] (#{:hidden :flagged} (:state cell))))))

(defn mines
  [game]
  (->> game
       (filter (fn [[_ cell]] (= :boom (:value cell))))))

(defn win?
  [game]
  (= (count (hidden-cells game)) (count (mines game))))

(defn toggle-flag
  [game coords]
  (update-in game [coords :state] {:flagged :hidden :hidden :flagged}))

(defn flag
  [game coords]
  (assoc-in game [coords :state] :flagged))

(defn should-play?
  "Returns false if game is not in playable state or given coords are
  protected from accidental clicking by flagging."
  [game coords]
  (let [cell-state (-> coords game :state)]
    (and (= :ok (:status game)) (not= :flagged cell-state))))

(defn play
  [game coords]
  (if (should-play? game coords)

    (let [game (reveal game coords) ;; Always reveal clicked cell
          v    (get-in game [coords :value])]

      (cond
        ;; Mine was revealed => end game with :boom
        (= :boom v) (assoc game :status v)

        ;; Last hidden 'non-mine' was reveled => end game with :win
        (win? game) (let [mines (map :coords (hidden-cells game))]
                      (-> (reduce flag game mines)
                          (assoc :status :win)))

        ;; Zero was revealed => recursively reveal connected zeros
        (zero? v) (let [zeros (hidden-zeros-around game coords)
                        game  (reveal-around game coords)]
                    (reduce play game zeros))

        ;; Non-zero number was revealed => game goes on
        :else game))

    game))
