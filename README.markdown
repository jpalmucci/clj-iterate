# clj-iterate - An iteration macro patterned after Common Lisp's Iterate

I wrote clj-iterate after using Daniel Janus excellent [clj-iter] [1]
package. I needed a larger subset of common lisp's iterate than
clj-iter provided. Unfortunately the bit I needed (the INTO clause)
required a rewrite.

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

* Driver clauses specify values to iterate over
* Gatherer clauses collect information during the iteration
* Control flow clauses can cause early termination of the loop

For example:

        user> (iter {:for x :from 1 :to 10}
                    {:collect x :into y}
                    {:return y :if (= x 5)})
        (1 2 3 4 5)
        user> 

`:For` is a driver clause, `:collect` is a gatherer clause, and
`:return` is a flow control clause.

Driver clauses are declarative. They have the same effect no matter
where they are placed in the iter form. Order is significant for the
other types of clauses. 

Example (compare to above):

        user> (iter {:for x :from 1 :to 10}
                    {:return y :if (= x 5)}
                    {:collect x :into y})
        (1 2 3 4)
        user> 
        
Also:

        user> (iter {:return y :if (= x 5)}
                    {:collect x :into y}
                    {:for x :from 1 :to 10})
        (1 2 3 4)
        user> 
        
## Iteration Using Primitive Types

Some clauses that introduce a new loop variable take a `:type`
option. If given, the loop variable will be of that primitive type,
making the code more efficient.

For example:

        user> (time (iter {:repeat 10000000}
                          {:sum 1}))
        "Elapsed time: 421.595 msecs"
        10000000
        user> (time (iter {:repeat 10000000}
                          {:sum 1 :type int}))
        "Elapsed time: 249.043 msecs"
        10000000
        user> 

## Driver Clauses

Driver clauses are all executed in parallel. The loop terminates when
any of the driver clauses reach their stopping criteria.

        user> (iter {:for x :from 1 :to 5}
                    {:for y :downfrom 5 :to -100}
                    {:collect [x y]})
        ([1 5] [2 4] [3 3] [4 2] [5 1])
        user> 


### Numeric Driver Clauses

Clj-iterate supports the following numeric driver clauses:

        {:for var :from expr [:to expr] [:by expr] [:type type]} 

Iterates over the integers from the `:from` expr to the `:to` expr. If
`:to` is missing, this clause will count forever.  If there is a 'by'
clause increment by that amount on each iteration.

        {:for var :downfrom expr [:to expr] [:by expr] [:type type]}

Same as the `:from` form, except we are counting down instead of
up. Note: the `:by` expression must be negative (or absent) for the
loop to terminate.

        {:repeat n [:using var]}

Repeat the loop `n` times. If the `using` option is present, expose
the iteration variable by that name.

### Sequence Driver Clauses

        {:for var :in expr}

Iterates over the sequence returned by `expr`, binding `var` to each
element. 

Note that there is no `:type` option for this clause because the items
in the sequence are probably already boxed. Adding a type declaration
would probably slow the loop down.

        {:for var :on expr}

Iterates over successive subsequences of the sequence returned by
`expr`, binding `var` to each subsequence.

## Gatherer Clauses

All gather clauses support two options: `:into` and `:if`.

`:Into` is used to gather the results into a new loop variable. If a
gatherer clause does not have an `:into` option, the values are
collected into a hidden variable which will be the return value of the
iter expression. If more than one gather clause is missing the `:into`
option, the results are undefined. If no gather clauses are missing
the `:into` option, you can use the `:returning` clause to specify a value
to return at the end of the loop.

If a gather clause has an `:if` option, the clause will only collect the
value if the provided expression is true.

Here is an example illustrating these options:

        user> (iter {:for x :from 0 :to 10}
                    {:collect x :into evens :if (even? x)}
                    {:collect x :into odds :if (odd? x)}
                    {:returning [evens odds]})
        [(0 2 4 6 8 10) (1 3 5 7 9)]
        user> 

Clj-iterate supports the following gatherer clauses:

        {:sum expr [ :into var ] [ :if pred ] [:type type]}

Sum the `expr` over all loop iterations.

        {:multiply expr [ :into var ] [ :if pred ] [:type type]}

Mutiply the `expr` together. Return 1 if there are no values.

        {:collect expr [ :into var ] [ :if pred ] }

Collect `expr` into a sequence. Specifically a persistent queue.

        {:reduce expr :by fn  [:initially expr] [ :into var ] [ :if pred ] [:type type]}

Reduce the values returned by `expr` using `fn`. `Iter` mimics the
clojure `reduce` function in that if zero values are reduced, the
result is the function applied with no arguments.

For example, if `iter` did not have a sum clause you could implement
it like this:

        (iter {:for x :from 1 :to 10} 
              {:reduce x :by +})

If you provide an initial value with `:initially`, that value is used
as a starting point for the reduction. `Iter` can generate more
efficient code with the `:initially` option, and the reduction
function need not accept zero elements. Therefore, we could implement
`:sum` more efficiently as follows:

         (iter {:for x :from 1 :to 10} 
               {:reduce x :by + :initially 0})

This is, in fact, how the `:sum` clause is implemented.

## Control Flow Clauses

        {:return expr :if pred}

If `pred` is true, immediately exit the loop returning `expr`.

## Clause Quick Reference

### Drivers 

            {:for var :from expr [:to expr] [:by expr] [:type type]} 
            {:for var :downfrom expr [:to expr] [:by expr] [:type type]}
            {:repeat n [:using var]}        
            {:for var :in expr}
            {:for var :on expr}

### Gatherers

            {:sum expr [ :into var ] [ :if pred ] [:type type]}
            {:multiply expr [ :into var ] [ :if pred ] [:type type]}
            {:collect expr [ :into var ] [ :if pred ]}
            {:reduce expr :by fn  [ :initially expr ] [ :into var ] [ :if pred ]  [:type type]}
            {:returning expr}

### Control Flow

            {:return expr :if pred}

[1]: http://github.com/nathell/clj-iter
