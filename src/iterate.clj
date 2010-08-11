(ns iterate
  (:use clojure.test clojure.set))

(defonce *reduce-marker* (Object.))

;; stub implementation (the real implementation uses the iter macro, below)
(defn- check-form [form required optional]
  )

(defmacro iter [ & body ]
  (let [initial-bindings (atom ()) ;; initial loop variable bindings in [var binding var binding .. ] form
        recur-vals (atom ()) ;; recursion values for the 'initial-bindings', in the same order as the initial-bindings
        let-bindings (atom ()) ;; variable computed based on current loop values that don't need to recur themselves
        side-effects (atom ()) ;; arbitrary forms to evaluate on each iteration
        return-tests (atom ()) ;; tests to see if the recursion is done
        return-value (atom nil) ;; value to return
        non-local-returns (atom ()) ;; test and value for special case return values (:return :if clause)
        ]
    (loop [body body]
      (if (empty? body)
        `(loop ~ (apply vector @initial-bindings)
           (let ~ (apply vector @let-bindings)

             (cond (or ~@ @return-tests)
                   ~ @return-value

                     ~@(apply concat @non-local-returns)
                     
                     true
                     (do
                       ~@(reverse @side-effects)
                       (recur ~@ @recur-vals)))))
        
        (let [form (first body)]
          (cond (list? form)
                ;; a list is arbitrary clojure code for the body of the iteration
                (do
                  (swap! side-effects #(cons form %))
                  (recur (rest body)))

                (map? form)
                ;; maps identify iteration clauses
                (let [form-keys (set (keys form))]
                  (cond (contains? form :returning)
                        (do
                          (check-form form #{:returning} #{})
                          (swap! return-value (fn [x] (:returning form)))
                          (recur (rest body)))

                        (contains? form :return)
                        (do
                          (check-form form #{:return :if} #{})
                          (swap! non-local-returns #(concat % `((~(:if form) ~(:return form)))))
                          (recur (rest body)))

                        (contains? form :repeat)
                        (let [iter-var (gensym)]
                          (check-form form #{:repeat} #{})
                          (recur (cons {:for iter-var :from 1 :to (:repeat form)}
                                       (rest body))))
                        
                        ;; integer counting with termination
                        (subset? [:for :from :to] form-keys)
                        (do
                          (check-form form #{:for :from :to} #{:by})
                          (swap! return-tests #(cons `(> ~(:for form) ~(:to form)) %))
                          (recur (cons (dissoc form :to)
                                       (rest body))))

                        ;; integer counting
                        (subset? [:for :from] form-keys)
                        (do
                          (check-form form #{:for :from} #{:by})
                          (recur (cons (if (contains? form :by)
                                         {:for (:for form) :initially (:from form) :then `(+ ~(:by form) ~(:for form))}
                                         {:for (:for form) :initially (:from form) :then `(inc ~(:for form))})
                                       (rest body))))

                        (subset? [:for :downfrom :to] form-keys)
                        (do
                          (check-form form #{:for :downfrom :to} #{:by})
                          (swap! return-tests #(cons `(< ~(:for form) ~(:to form)) %))
                          (recur (cons (dissoc form :to)
                                       (rest body))))

                        ;; integer counting
                        (subset? [:for :downfrom] form-keys)
                        (do
                          (check-form form #{:for :downfrom} #{:by})
                          (recur (cons (if (contains? form :by)
                                         {:for (:for form) :initially (:downfrom form) :then `(+ ~(:for form) ~(:by form))}
                                         {:for (:for form) :initially (:downfrom form) :then `(dec ~(:for form))})
                                       (rest body))))

                        (subset? [:for :in] form-keys)
                        (let [seq-var (gensym)]
                          (check-form form #{:for :in} #{})
                          (swap! initial-bindings #(concat % `( ~seq-var ~(:in form) )))
                          (swap! recur-vals #(concat % `( (rest ~seq-var) )))
                          (swap! let-bindings #(concat % `(~(:for form) (first ~seq-var))))
                          (swap! return-tests #(cons `(empty? ~seq-var) %))
                          (recur (rest body)))

                        (subset? [:for :on] form-keys)
                        (do
                          (check-form form #{:for :on} #{})
                          (swap! initial-bindings #(concat % `( ~(:for form) ~(:on form) )))
                          (swap! recur-vals #(concat % `( (rest ~(:for form)) )))
                          (swap! return-tests #(cons `(empty? ~(:for form)) %))
                          (recur (rest body)))
                        
                        (subset? [:for :initially :then] form-keys)
                        (do
                          (check-form form #{:for :initially :then} #{})
                          (swap! initial-bindings #(concat % `( ~(:for form) ~(:initially form) )))
                          (swap! recur-vals #(concat % `(~(:then form))))
                          (recur (rest body)))

                        ;; reduce implementation
                        (and (contains? form :reduce)
                             (not (contains? form :into)))
                        (let [into-var (gensym)]
                          (check-form form #{:reduce :by} #{:if :initially})
                          ;; if we don't have the :initially key, we need to use the reduce marker
                          (if (contains? form :initially)
                            (swap! return-value (fn [x] into-var))
                            (swap! return-value (fn [x] `(if (= ~into-var *reduce-marker*)
                                                           (~(:by form))
                                                           ~into-var))))
                          (recur (cons (assoc form :into into-var)
                                       (rest body))))

                        (subset? [:reduce :initially] form-keys)
                        ;; since we have :initially, we don't need to
                        ;; use the *reduce-marker* and can generate more
                        ;; efficient code
                        (do
                          (check-form form #{:reduce :by :into :initially} #{:if})
                          (swap! initial-bindings #(concat % `( ~(:into form) ~(:initially form) )))
                          (swap! recur-vals #(concat % (if (nil? (:if form))
                                                         `((~(:by form) ~(:into form) ~(:reduce form)))
                                                         `((if ~(:if form)
                                                             (~(:by form) ~(:into form) ~(:reduce form))
                                                             ~(:into form))))))
                          (recur (rest body)))

                        (contains? form :reduce)
                        ;; reduce case without :initially
                        (do
                          (check-form form #{:reduce :by :into} #{:if})
                          (swap! initial-bindings #(concat % `( ~(:into form) *reduce-marker* )))
                          (swap! recur-vals #(concat % (if (nil? (:if form))
                                                         `((cond (= ~(:into form) *reduce-marker*)
                                                                 ~(:reduce form)
                                                                 (true
                                                                  (~(:by form) ~(:into form) ~(:reduce form)))))

                                                         `((cond (not ~(:if form))
                                                                 ~(:into form)
                                                                 (= ~(:into form) *reduce-marker*)
                                                                 ~(:reduce form)
                                                                 true
                                                                 (~(:by form) ~(:into form) ~(:reduce form)))))))
                          (recur (rest body)))

                        (contains? form :sum)
                        (do (check-form form #{:sum} #{:into :if})
                            (recur (cons (merge {:reduce (:sum form) :by '+ :initially 0}
                                                (dissoc form :sum))
                                         (rest body))))

                        (contains? form :multiply)
                        (do (check-form form #{:multiply} #{:into :if})
                            (recur (cons (merge {:reduce (:multiply form) :by '* :initially 1}
                                                (dissoc form :multiply))
                                         (rest body))))

                        (contains? form :collect)
                        (do (check-form form #{:collect} #{:into :if})
                            (recur (cons (merge {:reduce (:collect form) :by conj :initially '(clojure.lang.PersistentQueue/EMPTY)}
                                                (dissoc form :collect))
                                         (rest body))))
                        
                        true
                        (throw (java.lang.Exception. (str "Unparsable iter form " form) ))))))))))

(defn- check-form [form required optional]
  "Utility to check the syntax of iter clauses"
  (iter {:for x :in required}
        (if (not (contains? form x))
          (throw (Exception. (str form "does not contain the required keyword " x)))))
  (iter {:for [key value] :in form}
        (if (and (not (contains? required key))
                 (not (contains? optional key)))
          (throw (Exception. (str form " contains the unknown keyword " key))))))