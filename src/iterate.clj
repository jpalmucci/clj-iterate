(ns iterate
  (:use clojure.test clojure.set clojure.walk))

;; stub implementation (the real implementation uses the iter macro, below)
(defn- check-form [form required optional]
  )

;; used to represent a reduction with zero items in it
(defonce *reduce-marker* (Object.))

(defn- merge-checking-frames
  "Merge the downstream return value of iter-expand with this new chunk, checking to make sure there are no errors"
  [cur downstream]
  (let [duplicate-var-names (intersection
                             (set (map first (partition 2 (cur :initial))))
                             (set (map first (partition 2 (downstream :initial)))))]
    (if (not (empty? duplicate-var-names))
      (throw (java.lang.Exception. (str "Attempted to introduce 2 loop variables with the same name: " duplicate-var-names))))
    (merge-with concat cur downstream)))

(defn- assoc-checking-frames
  "assoc the new key values with the downstream return value of
  iter-expand with this new chunk, checking to make sure there are no
  errors"
  [downstream & keys-and-values]
  (if (empty? keys-and-values)
        downstream
        (let [ [key value & rest] keys-and-values ]
          (cond (= key :initial)
                (let [duplicate-var-names (intersection
                                           #{(first value)}
                                           (set (map first (partition 2 (downstream :initial)))))]
                  (if (not (empty? duplicate-var-names))
                    (throw (java.lang.Exception. (str "Attempted to introduct 2 loop variables with the same name: " duplicate-var-names))))))
          (apply assoc-checking-frames (assoc downstream key value) (next (next keys-and-values))))))

;; take the body of an iter and expand it into a map of the following items:
;; :initial - initial loop variable bindings in [var binding var binding .. ] form
;; :recur - recursion values for the :initial, in the same order
;; :iteration-lets- - variables computed based on current loop values that don't need to recur themselves. calculated after the return tests
;; :lets- - variables computed and bound outside the loop
;; :return-val - the value to return when the looping terminates
;; :return-tests - a list of predicates. if any eval to true, looping is finished
;; :post - a list of var form pair that should be applied to the variable before returning
;; :code - code that should be evalled each time through the loop
(defn iter-expand [body]
  (cond (empty? body)
        {:initial () :recur () :code nil :return-tests ()}

        (map? (first body))
        ;; we have a map, which means it is an iteration clause
        (let [form ;; intern all the keys so that equality checks don't get confused by packages
              (apply assoc {} (mapcat (fn [[key val]] [(clojure.lang.Keyword/intern (name key)) val]) (first body))) 
              form-keys (set (keys form))]
          (cond                   

           (contains? form :return-if)
           (do
             (check-form form #{:return-if} #{})
             (let [downstream (iter-expand (rest body))]
               (assoc-checking-frames downstream
                 :return-tests
                 (cons (:return-if form) (downstream :return-tests)))))

           (contains? form :returning)
           (do
             (check-form form #{:returning} #{})
             (assoc-checking-frames (iter-expand (rest body))
                                    :return-val (:returning form)))

           (subset? [:for :=] form-keys)
           (do
             (check-form form #{:for :=} #{:type})
             (merge-checking-frames
                         {:iteration-lets `(~(if (form :type)
                                               (with-meta (:for form) {:tag (form :type)})
                                               (:for form))
                                            ~(:= form))}
                         (iter-expand (rest body))))

           (contains? form :repeat)
           (do
             (check-form form #{:repeat} #{:using})
             (let [iter-var (if (nil? (:using form))
                              (gensym)
                              (:using form))]
               (iter-expand (cons {:for iter-var :from 1 :to (:repeat form) :type 'int}
                                  (rest body)))))
           
           (subset? [:for :from :to] form-keys)
           (merge-checking-frames
            (iter-expand (cons (dissoc form :to) (rest body)))
            (let [var (gensym)]
              {:lets (if (form :type)
                       `(~var (~(form :type) ~(form :to)))
                       `(~var ~(form :to)))
               :return-tests `((> ~(:for form) ~var))}))

           (subset? [:for :downfrom :to] form-keys)
           (do
             (check-form form #{:for :downfrom :to} #{:by :type})
             (let [downstream (iter-expand (cons (assoc (dissoc form :downfrom :to)
                                                   :from (:downfrom form)
                                                   :by (or (:by form) -1))
                                                 (rest body)))]
               (assoc-checking-frames downstream
                 :return-tests (cons `(<  ~(:for form) ~(:to form)) (:return-tests downstream)))))

           (subset? [:for :downfrom] form-keys)
           (do
             (check-form form #{:for :downfrom} #{:by :type})
             (iter-expand (cons (assoc (dissoc form :downfrom)
                                  :from (:downfrom form)
                                  :by (or (:by form) -1))
                                (rest body))))

           (subset? [:for :from] form-keys)
           (do 
             (check-form form #{:for :from} #{:by :type})
             (iter-expand (cons {:for (:for form) 
                                 :initially (if (not (nil? (:type form)))
                                              `(~(:type form) ~(:from form))
                                              (:from form))
                                 :then (if (contains? #{'int 'long 'float 'double} (:type form))
                                         `(+ ~(:for form) (~(:type form) ~(or (:by form) 1)))
                                         (if (nil? (:by form))
                                           `(inc ~(:for form))
                                           (if (nil? (:type form))
                                             `(+ ~(:for form)  ~(:by form))
                                             `(~(:type form)  (+ ~(:for form)  ~(:by form))))))}
                                (rest body))))

           (subset? [:for :in] form-keys)
           (let [seq-var (gensym)]
             (check-form form #{:for :in} #{})
             (merge-checking-frames
                         {:initial (list seq-var (:in form))
                          :recur `((next ~seq-var))
                          :iteration-lets (list (:for form) `(first ~seq-var))
                          :return-tests `((empty? ~seq-var))}
                         (iter-expand (rest body))))

           (subset? '(:for :on) form-keys)
           (do
             (check-form form #{:for :on} #{})
             (if (symbol? (form :for))
               (merge-checking-frames 
                         (iter-expand (rest body))
                         {:initial (list (:for form) (:on form))
                          :recur `((next ~(:for form)))
                          :return-tests `((empty? ~(:for form)))})
               ;; handle destructuring
               (let [iter-var (gensym)]
                 (iter-expand `({for iter-var# on ~(form :on)}
                                {for ~(form :for) = iter-var#}
                                ~@(rest body))))))

           (subset? [:for :initially] form-keys)
           (do (check-form form #{:for :initially :then} #{:until :type})
               (if (contains? form :until)
                 (merge-checking-frames
                  (iter-expand (cons (dissoc form :until) (rest body)))
                  {:return-tests `(~(:until form))})
                 (merge-checking-frames
                  {:initial [(:for form) (if (contains? form-keys :type)
                                           `(~(:type form) ~(:initially form))
                                           (:initially form))]
                   :recur [(if (contains? form-keys :type)
                             `(~(:type form) ~(:then form))
                             (:then form))]}
                  (iter-expand (rest body)))))
                    
           ;; reduce case with :initially
           (subset? [:reduce :into :initially] form-keys)
           (do
             (check-form form #{:reduce :by :into :initially} #{:if :type :post})
             (let [downstream (iter-expand (rest body))
                   new-value-code (if (nil? (:if form))
                                                      `(~(:by form) ~(:into form) ~(:reduce form))
                                                      `(if ~(:if form)
                                                         (~(:by form) ~(:into form) ~(:reduce form))
                                                         ~(:into form)))]
               (merge-checking-frames downstream
                      {:initial [(:into form) (if (nil? (:type form))
                                                      (:initially form)
                                                      `(~(:type form) ~(:initially form)))]
                       :recur [(if (nil? (:type form))
                                      new-value-code
                                      `(~(:type form) ~new-value-code))]
                       :post (if (:post form)
                               [(:into form) `(~(:post form) ~(:into form))]
                               [])})))

           (subset? [:reduce :initially] form-keys)
           (do
             (check-form form #{:reduce :by :initially} #{:if :type :post})
             (let [out-var (gensym)
                   downstream (iter-expand (cons (assoc form :into out-var) (rest body)))]
               (assoc-checking-frames downstream
                 :return-val out-var)))

           ;; reduce case without :initially
           (subset? [:reduce :into] form-keys)
           (do
             (check-form form #{:reduce :by :into} #{:if :type})
             (let [downstream (iter-expand (rest body))]
               (assoc-checking-frames downstream
                 :initial (list* (:into form) '*reduce-marker* (:initial downstream))
                 :recur (cons (if (nil? (:if form))
                                `(cond (= ~(:into form) *reduce-marker*)
                                       ~(:reduce form)
                                       true
                                       (~(:by form) ~(:into form) ~(:reduce form)))
                                `(cond (not ~(:if form))
                                       ~(:into form)
                                       (= ~(:into form) *reduce-marker*)
                                       ~(:reduce form)
                                       true
                                       (~(:by form) ~(:into form) ~(:reduce form)))) (:recur downstream)))))

           (contains? form :reduce)
           ;; reduce without into, no intitially
           (let [into-var (gensym)
                 downstream (iter-expand (cons (assoc form :into into-var)
                                       (rest body)))]
             (check-form form #{:reduce :by} #{:if :type})
             (assoc-checking-frames downstream
               :return-val into-var
               :post (list* into-var `(if (= ~into-var *reduce-marker*) nil ~into-var) (:post downstream))))


           (contains? form :collect)
           (do (check-form form #{:collect} #{:into :if :type :initially})
               (iter-expand (cons (assoc (dissoc form :collect)
                                    :reduce (:collect form)
                                    :by 'conj
                                    :initially (if (contains? form :initially)
                                                 (form :initially)
                                                 '(clojure.lang.PersistentQueue/EMPTY))
                                    :post '(fn [x] (seq x)))
                                  (rest body))))

           (contains? form :sum)
           (do (check-form form #{:sum} #{:into :if :type})
               (iter-expand (cons (assoc (dissoc form :sum)
                                    :reduce (:sum form)
                                    :by (if (contains? #{'int 'long} (:type form))
                                          'unchecked-add
                                          '+)
                                    :initially 0)
                                  (rest body))))

           (contains? form :mean)
           (do (check-form form #{:mean} #{:if})
               (let [count (gensym) sum (gensym)]
                 (merge-checking-frames
                             {:initial (list count '(int 0) sum '(double 0))
                              :recur (if (:if form)
                                       `((if ~(:if form) (inc ~count) ~count)
                                         (if ~(:if form) (double (+ ~sum ~(:mean form))) ~sum))
                                       `((inc ~count) (double (+ ~sum ~(:mean form)))))}
                             (assoc (iter-expand (rest body))
                               :return-val `(/ ~sum ~count)))))

           (contains? form :max)
           (do (check-form form #{:max} #{:into :if :type :by})
               (iter-expand (cons (assoc (dissoc form :max)
                                    :reduce (:max form)
                                    :by (if (:by form)
                                          `(fn [a# b#]
                                             (if (> (.compare ^java.util.Comparator ~(:by form) a# b#) 0)
                                               a#
                                               b#))
                                                                
                                          :max))
                                  (rest body))))

           (contains? form :min)
           (do (check-form form #{:min} #{:into :if :type :by})
               (iter-expand (cons (assoc (dissoc form :min :by)
                                    :reduce (:min form)
                                    :by (if (:by form)
                                          `(fn [a# b#]
                                             (if (< (.compare ^java.util.Comparator ~(:by form) a# b#) 0)
                                               a#
                                               b#))
                                                                
                                          'min))
                                  (rest body))))

           (contains? form :multiply)
           (do (check-form form #{:multiply} #{:into :if :type})
               (iter-expand (cons (assoc (dissoc form :multiply)
                                    :reduce (:multiply form)
                                    :by (if (contains? #{'int 'long} (:type form))
                                          'unchecked-multiply
                                          '*)
                                    :initially 1)
                                  (rest body))))

           (contains? form :assoc)
           (do (check-form form #{:assoc :key} #{:by :into :if :initially})
               (iter-expand (cons (merge
                                   (dissoc form :assoc :key :by :initially)
                                   {:reduce (:assoc form) 
                                    :initially '(transient {})
                                    :post '(fn [x] (persistent! x))
                                    :by (if (nil? (:by form))
                                          `(fn [map# val#]
                                             (assoc! map# ~(:key form) val#))
                                          (let [val-sym (gensym)]
                                            `(fn [map# ~val-sym]
                                               (let [key# ~(:key form)]
                                                 (assoc! map# key#
                                                         (if (not (nil? (get map# key#)))
                                                           ;; work around the contains? bug
                                                           ;; TODO take out, won't work when you store a nil
                                                           ;; (contains? map# key#)
                                                           (~(:by form) (get map# key#) ~val-sym)
                                                           ~(if (:initially form)
                                                              `(~(:by form) ~(:initially form) ~val-sym)
                                                              val-sym)))))))})
                                  (rest body))))

           (contains? form :conj)
           (do (check-form form #{:conj} #{:into :if})
               (iter-expand (cons (assoc
                                   (dissoc form :conj)
                                   :reduce (:conj form) 
                                   :initially '(transient #{})
                                   :post '(fn [x] (persistent! x))
                                   :by 'conj!)
                                  (rest body))))

           (subset? '(:merge :by) form-keys)
           (do (check-form form #{:merge :by} #{:into :if})
               (iter-expand (cons (assoc
                                      (dissoc form :merge :by)
                                    :reduce (:merge form) 
                                    :initially '{}
                                    :by `(fn [x# y#] (merge-with ~(form :by) x# y#)))
                                  (rest body))))
           
           (contains? form :merge)
           (do (check-form form #{:merge} #{:into :if})
               (iter-expand (cons (assoc
                                   (dissoc form :merge)
                                   :reduce (:merge form) 
                                   :initially '{}
                                   :by 'merge)
                                  (rest body))))

           (contains? form :concat)
           (do (check-form form #{:concat} #{:into :if})
               (iter-expand (cons (assoc
                                   (dissoc form :concat)
                                   :reduce (:concat form) 
                                   :initially '()
                                   :by concat)
                                  (rest body))))

           true
           (throw (java.lang.Exception. (str "Unparsable iter form " form) ))))
        
        true
        ;; not an iter clause, just code
        (merge-checking-frames
                    {:code (list (first body))}
                    (iter-expand (rest body)))))

(defmacro iter [& body]
  (let [parse (iter-expand body)]
    `(let ~(apply vector (:lets parse))
       (loop ~(apply vector (:initial parse))
         (cond (or ~@(:return-tests parse))
               (let ~(apply vector (:post parse))
                 ~(:return-val parse))
               true
               (let ~(apply vector (:iteration-lets parse))
                 (do ~@(:code parse)
                     (recur ~@(:recur parse)))))))))

(defn- check-form [form required optional]
  "Utility to check the syntax of iter clauses"
  (iter {for x in required}
        (if (not (contains? form x))
          (throw (Exception. (str form " does not contain the required keyword " x)))))
  (iter {for [key value] in form}
        (if (and (not (contains? required key))
                 (not (contains? optional key)))
          (throw (Exception. (str form " contains the unknown keyword " key))))))

