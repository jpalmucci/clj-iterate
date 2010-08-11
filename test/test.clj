(ns test
  (:use clojure.test iterate))

(deftest iter-test
  (is (= (iter {:for x :from 0 :to 10}
                {:collect x :if (even? x)})
         [0 2 4 6 8 10]))

  (is (= (iter {:for x :from 0 :to 10 :by 2}
                {:collect x :if (even? x)})
         [0 2 4 6 8 10]))

  (is (= (iter {:for x :downfrom 10 :to 0 :by -2}
                       {:collect x :if (even? x)})
         [10 8 6 4 2 0]))

  (is (= (iter {:for x :from 0 :to 10}
                {:sum x})
         (+ 0 1 2 3 4 5 6 7 8 9 10)))

  (is (= (iter {:for x :from 0 :to 10}
                {:sum x :into y}
                {:returning [y]})
         [(+ 0 1 2 3 4 5 6 7 8 9 10)]))

  (is (= (iter {:for x :from 0 :to 10}
                {:sum x}
                {:return :foo :if (= x 5)})
         :foo))

  (is (= (iter {:for x :from 0 :to 10}
                {:sum x}
                {:return :foo :if (= x 50)})
         (+ 0 1 2 3 4 5 6 7 8 9 10)))

  (is (= (iter {:for x :from 1 :to 10}
                {:multiply x})
         (* 1 2 3 4 5 6 7 8 9 10)))

  (is (= (iter {:for x :downfrom 10 :to 1}
                {:collect x})
         [10 9 8 7 6 5 4 3 2 1]))

  (is (= (iter {:for x :downfrom 10}
                {:for y :from 1 :to 3}
                {:collect x})
         [10 9 8]))

  (is (= (iter {:for x :in (range 0 10)}
                {:sum x})
         (+ 0 1 2 3 4 5 6 7 8 9)))

  (is (= (iter {:for x :from 0 :to 10}
                {:sum x :if (even? x)})
         30))

  (is (= (iter {:for x :from 1 :to 10}
                {:multiply x :if (even? x)})
         (* 2 4 6 8 10)))

  (is (= (iter {:for x :from 1 :to 9}
                {:collect x})
         (range 1 10)))

  (is (= (iter {:for x :from 1 :to 9}
                {:collect x :if (odd? x)})
         (filter odd? (range 1 10))))

  (is (= (iter {:repeat 100}
                {:sum 1})
         100))
  (is (= (iter {:for x :from 1 :to 10}
               (println x))
         nil))

  ;; synonym for collect
  #_
  (iter {:for x :from 1 :to 10}
         {:reduce x :by conj :initially (clojure.lang.PersistentQueue/EMPTY) :into foo}
         {:returning (seq foo)}
         )

  (is (= (iter {:for x :on (range 0 3)}
                {:collect x})
         '((0 1 2) (1 2) (2))))

  ;; interesting example using :into
  (is (= (iter {:for x :from 0 :to 100}
                {:collect x :into evens :if (even? x)}
                {:collect x :into odds :if (odd? x)}
                {:returning [(seq evens) (seq odds)]})
         [(filter even? (range 0 101)) (filter odd? (range 0 101))])))