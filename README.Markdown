clj-iterate - An iteration macro patterned after Common Lisps Iterate
=====================================================================

I wrote clj-iterate after using Daniel Janus excellent clj-iter
package. I needed a larger subset of common lisp's iterate than
clj-iter provided. 

The 'iter' macro takes a list of expressions. Lists are treated as
clojure code to be executed inside the loop and maps are treated as
iteration clauses.

          user> (iter {:for x :from 1 :to 10}
                      (println "On" x)
                      {:collect x})
          On 1
          On 2
          On 3
          On 4
          On 5
          On 6
          On 7
          On 8
          On 9
          On 10
          (1 2 3 4 5 6 7 8 9 10)
          user> 

The macro expands into a loop/recur form that is side-effect free and
typically very fast.

There are several different types of iteration clauses:
* Driver clauses cause iteration to go forward.
* Gatherer clauses collect information during the iteration
* Control flow clauses can cause early termination of the loop

For example:

        user> (iter {:for x :from 1 :to 10}
                    {:collect x :into y}
                    {:return y :if (= x 5)})
        (1 2 3 4)
        user> 


