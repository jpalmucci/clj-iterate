(ns test
  (:use clojure.test)
  (:use iterate))

(deftest iter-test
  (is (= (iter {for x from 0 to 10}
               {collect x if (even? x)})
         [0 2 4 6 8 10]))

  (is (= (iter {for x from 0 to 10 by 2}
               {collect x if (even? x)})
         [0 2 4 6 8 10]))

  (is (= (iter {for x from 0 to 10 by 2 type int}
               {collect x if (even? x)})
         [0 2 4 6 8 10]))

  (is (= (iter {for x from 0.0 to 10.0 by 2.0 type float}
               {collect x})
         [0.0 2.0 4.0 6.0 8.0 10.0]))

  (is (= (iter {for x downfrom 10 to 0 by -2}
               {collect x if (even? x)})
         [10 8 6 4 2 0]))

  (is (= (iter {for x from 0 to 10}
               {sum x})
         (+ 0 1 2 3 4 5 6 7 8 9 10)))

  (is (= (iter {for x from 0 to 10 type int}
               {sum x})
         (+ 0 1 2 3 4 5 6 7 8 9 10)))

  (is (= (iter {for x from 0 to 10}
               {sum x into y}
               {returning [y]})
         [(+ 0 1 2 3 4 5 6 7 8 9 10)]))

  (is (= (iter {for x from 0 to 10}
               {sum x}
               {return-if (= x 5)})
         (+ 0 1 2 3 4)))

  (is (= (iter {for x from 0 to 10}
               {sum x}
               {return-if (= x 50)})
         (+ 0 1 2 3 4 5 6 7 8 9 10)))

  (is (= (iter {for x from 1 to 10}
               {multiply x})
         (* 1 2 3 4 5 6 7 8 9 10)))

  (is (= (iter {for x downfrom 10 to 1}
               {collect x})
         [10 9 8 7 6 5 4 3 2 1]))

  (is (= (iter {for x downfrom 10}
               {for y from 1 to 3}
               {collect x})
         [10 9 8]))

  (is (= (iter {for x in (range 0 10)}
               {sum x})
         (+ 0 1 2 3 4 5 6 7 8 9)))

  (is (= (iter {for x from 0 to 10}
               {sum x if (even? x)})
         30))

  (is (= (iter {for x from 1 to 10}
               {multiply x if (even? x)})
         (* 2 4 6 8 10)))

  (is (= (iter {for x from 1 to 10}
               {multiply x if (even? x) type int})
         (* 2 4 6 8 10)))

  (is (= (iter {for x from 1 to 9}
               {collect x})
         (range 1 10)))

  (is (= (iter {for x from 1 to 9}
               {collect x if (odd? x)})
         (filter odd? (range 1 10))))

  (is (= (iter {repeat 100}
               {sum 1})
         100))

  (is (= (iter {repeat 100}
               {sum 1 type int})
         100))

  (is (= (iter {repeat 10 using x}
               {collect x})
         [1 2 3 4 5 6 7 8 9 10]))

  (is (= (iter {repeat 100}
               {sum 1 type int into x}
               {returning x})
         100))

  (is (= (iter {repeat 100}
               {sum 1.0 type float into x}
               {returning x})
         100.0))

  (is (= (iter {for x from 1 to 10}
               (println x))
         nil))

  (is (= (iter {for x on (range 0 3)}
               {collect x})
         '((0 1 2) (1 2) (2))))

  ;; interesting example using into
  (is (= (iter {for x from 0 to 100}
               {collect x into evens if (even? x)}
               {collect x into odds if (odd? x)}
               {returning [evens odds]})
         [(filter even? (range 0 101)) (filter odd? (range 0 101))]))

  (is (= (iter {for x from 1 to 5}
               {for y downfrom 5 to 1}
               {collect [x y]})
         [[1 5] [2 4] [3 3] [4 2] [5 1]])
      )

  (is (= (iter {for x from 1 to 10}
               {assoc x key x})
         {1 1 2 2 3 3 4 4 5 5 6 6 7 7 8 8 9 9 10 10}))

  (is (= (iter {for x from 1 to 10}
               {max x})
         10))

  (is (= (iter {for x from 1 to 10}
               {min x})
         1))

  (is (= (iter {for x from 1 to 10}
               {conj x})
         #{1 2 3 4 5 6 7 8 9 10}))

  (is (= (iter {for x from 1 to 10}
               {for y = (+ 10 x)}
               {conj y})
         #{11 12 13 14 15 16 17 18 19 20}))

  (is (= 
       (iter {for x from 1 to 100}
             {assoc (list x) key (mod x 10) by concat})
       '{0 (10 20 30 40 50 60 70 80 90 100),
         1 (1 11 21 31 41 51 61 71 81 91),
         2 (2 12 22 32 42 52 62 72 82 92),
         3 (3 13 23 33 43 53 63 73 83 93),
         4 (4 14 24 34 44 54 64 74 84 94),
         5 (5 15 25 35 45 55 65 75 85 95),
         6 (6 16 26 36 46 56 66 76 86 96),
         7 (7 17 27 37 47 57 67 77 87 97),
         8 (8 18 28 38 48 58 68 78 88 98),
         9 (9 19 29 39 49 59 69 79 89 99)}))

  (is (=
       (iter {for x from 1 to 100}
             {assoc x key (mod x 10) by conj initially ()})
       '{0 (100 90 80 70 60 50 40 30 20 10),
         1 (91 81 71 61 51 41 31 21 11 1),
         2 (92 82 72 62 52 42 32 22 12 2),
         3 (93 83 73 63 53 43 33 23 13 3),
         4 (94 84 74 64 54 44 34 24 14 4),
         5 (95 85 75 65 55 45 35 25 15 5),
         6 (96 86 76 66 56 46 36 26 16 6),
         7 (97 87 77 67 57 47 37 27 17 7),
         8 (98 88 78 68 58 48 38 28 18 8),
         9 (99 89 79 69 59 49 39 29 19 9)}))

    (is (=
         (iter {for x from 1 to 100}
             {assoc x key (mod x 10) by conj if (even? x) initially ()})
       '{0 (100 90 80 70 60 50 40 30 20 10),
         2 (92 82 72 62 52 42 32 22 12 2),
         4 (94 84 74 64 54 44 34 24 14 4),
         6 (96 86 76 66 56 46 36 26 16 6),
         8 (98 88 78 68 58 48 38 28 18 8)}))

    (is (= (iter {for x from 1 to 5}
                 {merge
                  {x (+ x 1)}})
           {1 2 2 3 3 4 4 5 5 6}))

  )