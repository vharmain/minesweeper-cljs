(ns app.specs
  (:require
   [clojure.spec.alpha :as s]))

(s/def :cell/coord (s/int-in 0 100))
(s/def :cell/coords (s/tuple :cell/coord :cell/coord))
(s/def :cell/state #{:cell.state/boom :cell.state/visible :cell.state/hidden})

(s/def :cell.value/num (s/int-in 0 9))
(s/def :cell.value/boom #{:cell.value/boom})

(s/def :cell/value
   (s/or
    :num :cell.value/num
    :boom :cell.value/boom))

(s/def :game/cell
  (s/keys :req [:cell/coords :cell/state :cell/value]))

(s/def :game/status #{:game.status/ok :game.status/boom})

(s/def :game/board
  (s/map-of :cell/coords :game/cell))

(s/def :game/status #{:game.status/ok :game.status/win :game.status/boom})
(s/def :game/mines-count (s/int-in 0 100))
(s/def :game/hints-count (s/int-in 0 3))

(s/def :game/game
  (s/keys :req [:game/board :game/status :game/mines-count :game/hints-count]))

(comment
  (s/valid? :cell.value/boom :cell.value/boom)
  (s/valid? :cell.value/num 100)


  (s/valid? :game/coords [0 1])
  (s/valid? :game/coords [0])

  (s/valid? :cell/value nil)
  (s/valid? :cell/value :cell.value/boom)
  (s/valid? :cell/value 5)
  (s/valid? :cell/value -1)

  (s/valid? :game/board
            {[0 0] {:cell/value 0
                    :cell/state :cell.state/hidden
                    :cell/coords [0 0]}}))
