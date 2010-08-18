(ns iterate
  (:use clojure.test clojure.set clojure.walk))

;; stub implementation (the real implementation uses the iter macro, below)
(defn- check-form [form required optional]
  )

;; used to represent a reduction with zero items in it
(defonce *reduce-marker* (Object.))

;; used to mark the place in the form where we want the recur to occur
(defonce *recur-marker* (Object.))

;; take the body of an iter and expand it into a map of the following items:
;; :initial - initial loop variable bindings in [var binding var binding .. ] form
;; :recur - recursion values for the :initial, in the same order
;; :lets - variables computed based on current loop values that don't need to recur themselves
;; :return-val - the value to return when the looping terminates
;; :return-tests - a list of forms. if any eval to true, looping is finished
;; :code - code that should be evalled each time through the loop
(defn iter-expand [body]
  (cond (empty? body)
        {:initial () :recur () :code (list *recur-marker*) :return-tests ()}

        (map? (first body))
        ;; we have a map, which means it is an iteration clause
        (let [form (first body)
              form-keys (set (keys form))]
          (cond                   

           (contains? form 'returning)
           (do
             (check-form form #{'returning} #{})
             (assoc (iter-expand (rest body))
               :return-val ('returning form)))

           (contains? form 'return)
           (do
             (check-form form #{'return 'if} #{})
             (let [downstream (iter-expand (rest body))]
               (assoc downstream
                 :code `((if ~('if form)
                           ~('return form)
                           (do ~@(:code downstream)))))))

           (contains? form 'repeat)
           (do
             (check-form form #{'repeat} #{'using})
             (let [iter-var (if (nil? ('using form))
                              (gensym)
                              ('using form))]
               (iter-expand (cons {'for iter-var 'from 1 'to ('repeat form) 'type 'int}
                                  (rest body)))))
           
           (subset? ['for 'from 'to] form-keys)
           (merge-with concat
                       (iter-expand (cons (dissoc form 'to) (rest body)))
                       {:return-tests `((> ~('for form) ~('to form)))})

           (subset? ['for 'downfrom 'to] form-keys)
           (do
             (check-form form #{'for 'downfrom 'to} #{'by 'type})
             (let [downstream (iter-expand (cons (assoc (dissoc form 'downfrom 'to)
                                                   'from ('downfrom form)
                                                   'by (or ('by form) -1))
                                                 (rest body)))]
               (assoc downstream
                 :return-tests (cons `(<  ~('for form) ~('to form)) (:return-tests downstream)))))

           (subset? ['for 'downfrom] form-keys)
           (do
             (check-form form #{'for 'downfrom} #{'by 'type})
             (iter-expand (cons (assoc (dissoc form 'downfrom)
                                  'from ('downfrom form)
                                  'by (or ('by form) -1))
                                (rest body))))

           (subset? ['for 'from] form-keys)
           (do 
             (check-form form #{'for 'from} #{'by 'type})
             (iter-expand (cons {'for ('for form) 
                                 'initially (if (not (nil? ('type form)))
                                              `(~('type form) ~('from form))
                                              ('from form))
                                 'then (if (contains? #{'int 'long} ('type form))
                                         `(unchecked-add ~('for form)  ~(or ('by form) 1))
                                         (if (nil? ('by form))
                                           `(inc ~('for form))
                                           (if (nil? ('type form))
                                             `(+  ~('for form)  ~('by form))
                                             `(~('type form)  (+  ~('for form)  ~('by form))))))}
                                (rest body))))

           (subset? ['for 'in] form-keys)
           (let [seq-var (gensym)]
             (check-form form #{'for 'in} #{})
             (merge-with concat 
                         (iter-expand (rest body))
                         {:initial (list seq-var ('in form))
                          :recur `((next ~seq-var))
                          :lets (list ('for form) `(first ~seq-var))
                          :return-tests `((empty? ~seq-var))}))

           (subset? ['for 'on] form-keys)
           (do
             (check-form form #{'for 'on} #{})
             (merge-with concat 
                         (iter-expand (rest body))
                         {:initial (list ('for form) ('on form))
                          :recur `((next ~('for form)))
                          :return-tests `((empty? ~('for form)))}))
           
           (subset? ['for 'initially 'then] form-keys)
           (do 
             (check-form form #{'for 'initially 'then} #{})
             (merge-with concat
                         {:initial [('for form) ('initially form)]
                          :recur [('then form)]
                          :code ()}
                         (iter-expand (rest body))))

           ;; reduce case with 'initially
           (subset? ['reduce 'into 'initially] form-keys)
           (do
             (check-form form #{'reduce 'by 'into 'initially} #{'if 'type})
             (let [downstream (iter-expand (rest body))
                   new-value-code (if (nil? ('if form))
                                                      `(~('by form) ~('into form) ~('reduce form))
                                                      `(if ~('if form)
                                                         (~('by form) ~('into form) ~('reduce form))
                                                         ~('into form)))]
               (merge downstream
                      {:initial (list* ('into form) (if (nil? ('type form))
                                                      ('initially form)
                                                      `(~('type form) ~('initially form)))
                                       (:initial downstream))
                       :recur (cons (if (nil? ('type form))
                                                      ('into form)
                                                      `(~('type form) ~('into form)))
                                    (:recur downstream))
                       :code `((let [~('into form) ~(if (nil? ('type form))
                                                      new-value-code
                                                      `(~('type form) ~new-value-code))]
                                 ~@(:code downstream)))})))

           (subset? ['reduce 'initially] form-keys)
           (do
             (check-form form #{'reduce 'by 'initially} #{'if 'type})
             (let [out-var (gensym)
                   downstream (iter-expand (cons (assoc form 'into out-var) (rest body)))]
               (assoc downstream :return-val out-var :return-val out-var)))

           ;; reduce case without 'initially
           (subset? ['reduce 'into] form-keys)
           (do
             (check-form form #{'reduce 'by 'into} #{'if 'type})
             (let [downstream (iter-expand (rest body))]
               (assoc downstream
                 :initial (list* ('into form) '*reduce-marker* (:initial downstream))
                 :recur (cons ('into form) (:recur downstream))
                 :code `((let [~('into form) ~(if (nil? ('if form))
                                                `(cond (= ~('into form) *reduce-marker*)
                                                       ~('reduce form)
                                                       true
                                                       (~('by form) ~('into form) ~('reduce form)))
                                                `(cond (not ~('if form))
                                                       ~('into form)
                                                       (= ~('into form) *reduce-marker*)
                                                       ~('reduce form)
                                                       true
                                                       (~('by form) ~('into form) ~('reduce form))))]
                           (do ~@(:code downstream)))))))

           (contains? form 'reduce)
           (let [into-var (gensym)]
             (check-form form #{'reduce 'by} #{'if 'type})
             (assoc (iter-expand (cons (assoc form 'into into-var)
                                       (rest body)))
               :return-val into-var))


           (contains? form 'collect)
           (do (check-form form #{'collect} #{'into 'if 'type})
               (iter-expand (cons (assoc (dissoc form 'collect)
                                    'reduce ('collect form)
                                    'by 'conj
                                    'initially '(clojure.lang.PersistentQueue/EMPTY))
                                  (rest body))))

           (contains? form 'sum)
           (do (check-form form #{'sum} #{'into 'if 'type})
               (iter-expand (cons (assoc (dissoc form 'sum)
                                    'reduce ('sum form)
                                    'by (if (contains? #{'int 'long} ('type form))
                                          'unchecked-add
                                          '+)
                                    'initially 0)
                                  (rest body))))

           (contains? form 'max)
           (do (check-form form #{'max} #{'into 'if 'type})
               (iter-expand (cons (assoc (dissoc form 'max)
                                    'reduce ('max form)
                                    'by 'max)
                                  (rest body))))

           (contains? form 'min)
           (do (check-form form #{'min} #{'into 'if 'type})
               (iter-expand (cons (assoc (dissoc form 'min)
                                    'reduce ('min form)
                                    'by 'min)
                                  (rest body))))

           (contains? form 'multiply)
           (do (check-form form #{'multiply} #{'into 'if 'type})
               (iter-expand (cons (assoc (dissoc form 'multiply)
                                    'reduce ('multiply form)
                                    'by (if (contains? #{'int 'long} ('type form))
                                          'unchecked-multiply
                                          '*)
                                    'initially 1)
                                  (rest body))))

           (contains? form 'assoc)
           ;; TODO: Make this a transient map because the iter loop is single threaded
           (do (check-form form #{'assoc 'key} #{'by 'into 'if 'initially})
               (iter-expand (cons (merge
                                   (dissoc form 'assoc 'key 'by 'initially)
                                   {'reduce ('assoc form) 
                                    'initially {}
                                    'by (if (nil? ('by form))
                                          `(fn [map# val#]
                                             (assoc map# ~('key form) val#))
                                          (let [val-sym (gensym)]
                                          `(fn [map# ~val-sym]
                                             (let [key# ~('key form)]
                                               (assoc map#
                                                 ~('key form)
                                                 (if (contains? map# key#)
                                                   (~('by form) (map# key#) ~val-sym)
                                                   ~(if ('initially form)
                                                      `(~('by form) ~('initially form) ~val-sym)
                                                      val-sym)))))))})
                                  (rest body))))
                                   
           

           true
           (throw (java.lang.Exception. (str "Unparsable iter form " form) ))))
        
        true
        ;; not an iter clause, just code
        (merge-with concat
                    {:code (list (first body))}
                    (iter-expand (rest body)))))

(defmacro iter [& body]
  (let [parse (iter-expand body)
        code (postwalk #(if (= % *recur-marker*) `(recur ~@(:recur parse)) %)
                       (:code parse))]
    `(loop ~(apply vector (:initial parse))
       (let ~(apply vector (:lets parse))
         (if (or ~@(:return-tests parse))
           ~(:return-val parse)
           (do ~@code))))))

(defn- check-form [form required optional]
  "Utility to check the syntax of iter clauses"
  (iter {for x in required}
        (if (not (contains? form x))
          (throw (Exception. (str form " does not contain the required keyword " x)))))
  (iter {for [key value] in form}
        (if (and (not (contains? required key))
                 (not (contains? optional key)))
          (throw (Exception. (str form " contains the unknown keyword " key))))))

