# clj-iterate - An iteration macro similar to Common Lisp's Iterate

I wrote clj-iterate after using Daniel Janus excellent [clj-iter] [1]
package. I needed a larger subset of common lisp's iterate than
clj-iter provided. Unfortunately the bit I needed (the INTO clause)
required a rewrite.

To use clj-iterate, add it to your lein project.clj, as follows:
       
        :dependencies [[clj-iterate "0.95-SNAPSHOT"]]

The 'iter' macro takes a list of expressions. Lists are treated as
clojure code to be executed inside the loop and maps are treated as
iteration clauses.

          user> (iter {for x from 1 to 10}
                      (println "On" x)
                      {collect x})
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

        user> (iter {for x from 1 to 10}
                    {return-if (= x 5)}
                    {collect x})
        (1 2 3 4)
        user> 

`For` is a driver clause, `collect` is a gatherer clause, and
`return` is a flow control clause.

Clauses are declarative. They have the same effect no matter where
they are placed in the iter form. Updates and tests occur in lockstep.

Therefore:

        user> (iter {for x from 1 to 10}
                    {collect x}
                    {return-if (= x 5)})
        (1 2 3 4)
        user> 

Order is significant for clojure code, of course.
        
## Iteration Using Primitive Types

Some clauses that introduce a new loop variable take a `type`
option. If given, the loop variable will be of that primitive type,
making the code more efficient.

For example:

        user> (time (iter {repeat 10000000}
                          {sum 1}))
        "Elapsed time: 421.595 msecs"
        10000000
        user> (time (iter {repeat 10000000}
                          {sum 1 type int}))
        "Elapsed time: 249.043 msecs"
        10000000
        user> 

## Driver Clauses

Driver clauses are all executed in parallel. The loop terminates when
any of the driver clauses reach their stopping criteria.

        user> (iter {for x from 1 to 5}
                    {for y downfrom 5 to -100}
                    {collect [x y]})
        ([1 5] [2 4] [3 3] [4 2] [5 1])
        user> 


### Numeric Driver Clauses

Clj-iterate supports the following numeric driver clauses:

####        {for var from expr [to expr] [by expr] [type type]} 

Iterates over the integers from the `from` expr to the `to` expr. If
`to` is missing, this clause will count forever.  If there is a 'by'
clause increment by that amount on each iteration.

####        {for var downfrom expr [to expr] [by expr] [type type]}

Same as the `from` form, except we are counting down instead of
up. Note: the `by` expression must be negative (or absent) for the
loop to terminate.

####        {repeat n [using var]}

Repeat the loop `n` times. If the `using` option is present, expose
the iteration variable by that name.

### Sequence Driver Clauses

####        {for var in expr}

Iterates over the sequence returned by `expr`, binding `var` to each
element. Destructuring is supported:

        user> (iter {for [key val] in {1 2 3 4} } 
                (println "Key" key "Val" val))
              Key 1 Val 2
              Key 3 Val 4
              nil
        user> 


Note that there is no `type` option for this clause because the items
in the sequence are probably already boxed. Adding a type declaration
would probably slow the loop down.

####        {for var on expr}

Iterates over successive subsequences of the sequence returned by
`expr`, binding `var` to each subsequence.

### Other Driver Clauses

####        {for var initially expr then expr [until expr] [type expr]}

General construct for doing `loop` like iteration within iter. Sets
var to `initially` on first pass, then to `then` on successive
passes. Terminates if the `until` expression is true.

## Gatherer Clauses

All gather clauses support two options: `into` and `if`.

`Into` is used to gather the results into a new loop variable. `Into`
clauses are extremely useful if you'd like to calculate more than one
result during a single pass through the data. You can store each
result into a new loop variable.

If a gatherer clause does not have an `into` option, the values are
collected into a hidden variable which will be the return value of the
iter expression. If more than one gather clause is missing the `into`
option, the results are undefined. If no gather clauses are missing
the `into` option, you can use the `returning` clause to specify a
value to return at the end of the loop.

If a gather clause has an `if` option, the clause will only collect the
value if the provided expression is true.

Here is an example illustrating these options:

        user> (iter {for x from 0 to 10}
                    {collect x into evens if (even? x)}
                    {collect x into odds if (odd? x)}
                    {returning [evens odds]})
        [(0 2 4 6 8 10) (1 3 5 7 9)]
        user> 

Clj-iterate supports the following gatherer clauses:

####        {sum expr [ into var ] [ if pred ] [type type]}

Sum the `expr` over all loop iterations.

####        {multiply expr [ into var ] [ if pred ] [type type]}

Mutiply the `expr` together. Return 1 if there are no values.

####        {max expr [ into var ] [ if pred ] [type type] [by comparator]}
####        {min expr [ into var ] [ if pred ] [type type] [by comparator]}
####        {mean expr [ if pred ]}

####        {collect expr [ into var ] [ if pred ] [initially expr]}

Collect `expr` into a sequence (specifically a persistent
queue). Optionally start with the collect specified by the `initially`
option, which can by any data structure that supports conj.

####        {reduce expr by reduce-fn [initially expr] [ into var ] [ if pred ] [type type]}

Reduce the values returned by `expr` using `reduce-fn`. `Iter` mimics the
clojure `reduce` function in that if zero values are reduced, the
result is the function applied with no arguments.

For example, if `iter` did not have a sum clause you could implement
it like this:

        (iter {for x from 1 to 10} 
              {reduce x by +})

If you provide an initial value with `initially`, that value is used
as a starting point for the reduction. `Iter` can generate more
efficient code with the `initially` option, and the reduction
function need not accept zero elements. Therefore, we could implement
`sum` more efficiently as follows:

         (iter {for x from 1 to 10} 
               {reduce x by + initially 0})

This is, in fact, how the `sum` clause is implemented.

#### {conj expr [ into var ] [ if pred ] [initially expr] }

Collect the values into a set (or the data type provided by the initially expr).

#### {concat expr [ into var ] [ if pred ] initially expr}

Concatenate the results. 

#### {assoc expr key key [by reduce-fn] [ initially expr ] [ into var ] [ if pred ]}

Create a map of the `expr` values indexed by `key`:

        user> (iter {for x from 1 to 10}
                    {assoc x key x})
              {1 1, 2 2, 3 3, 4 4, 5 5, 6 6, 7 7, 8 8, 9 9, 10 10}
        user> 

If `by` is specified, use that function to reduce values with the same key:

        user> (iter {for x from 1 to 100}
                    {assoc (list x) key (mod x 10) by concat})
        {0 (10 20 30 40 50 60 70 80 90 100),
         1 (1 11 21 31 41 51 61 71 81 91),
         2 (2 12 22 32 42 52 62 72 82 92),
         3 (3 13 23 33 43 53 63 73 83 93),
         4 (4 14 24 34 44 54 64 74 84 94),
         5 (5 15 25 35 45 55 65 75 85 95),
         6 (6 16 26 36 46 56 66 76 86 96),
         7 (7 17 27 37 47 57 67 77 87 97),
         8 (8 18 28 38 48 58 68 78 88 98),
         9 (9 19 29 39 49 59 69 79 89 99)}
        user> 

If 'initially' is specified, use that as the initial value for the
key-based reductions. For example, you could implement the above
example, creating less garbage, like so:

        user> (iter {for x from 1 to 100}
                    {assoc x key (mod x 10) by conj initially ()})
        {0 (100 90 80 70 60 50 40 30 20 10),
         1 (91 81 71 61 51 41 31 21 11 1),
         2 (92 82 72 62 52 42 32 22 12 2),
         3 (93 83 73 63 53 43 33 23 13 3),
         4 (94 84 74 64 54 44 34 24 14 4),
         5 (95 85 75 65 55 45 35 25 15 5),
         6 (96 86 76 66 56 46 36 26 16 6),
         7 (97 87 77 67 57 47 37 27 17 7),
         8 (98 88 78 68 58 48 38 28 18 8),
         9 (99 89 79 69 59 49 39 29 19 9)}
        user> 

#### {merge expr [by fn] [ into var ] [ if pred ] [initially expr]}

Expr should return a map. All maps are merged, as in the clojure merge
function. 

Keys that occur in more than one map are combined using the function
specified in the `by` option, which must take 2 arguments. If the `by`
option is not specified, entries in later iterations overwrite
previous iterations.

## Control Flow Clauses

####        {return-if pred}

If `pred` is true, immediately exit the loop..

####        {for var = expr [type type]}

Not really control flow. Defines a variable inside the loop body. 

If the `type` option is present, make the variable statically
typed. This is exactly equivalent to:

           {for ^type var = expr}

## Clause Quick Reference

### Drivers 

            {for var from expr [to expr] [by expr] [type type]} 
            {for var downfrom expr [to expr] [by expr] [type type]}
            {for var initially expr then expr [until expr] [type type]}
            {repeat n [using var]}        
            {for var in expr}
            {for var on expr}

### Gatherers

            {sum expr [ into var ] [ if pred ] [type type]}
            {multiply expr [ into var ] [ if pred ] [type type]}
            {max expr [ into var ] [ if pred ] [type type] [by comparator]}
            {min expr [ into var ] [ if pred ] [type type] [by comparator]}
            {mean expr [ if pred ]}
            {collect expr [ into var ] [ if pred ] [initially expr]}
            {reduce expr by reduce-fn  [ initially expr ] [ into var ] [ if pred ]  [type type]}
            {conj expr [ into var ] [ if pred ] [initially expr]}
            {concat expr [ into var ] [ if pred ] [initially expr]}
            {assoc expr key key [by reduce-fn] [ initially expr ] [ into var ] [ if pred ]}
            {merge expr [ by fn ] [ into var ] [ if pred ] [initially expr]}
            {returning expr}

### Control Flow

            {return-if pred}
            {for var = expr}

[1]: http://github.com/nathell/clj-iter
