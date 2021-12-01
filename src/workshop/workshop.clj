;; # Workshop
;; ## 👋 XTDB Workshop - Re:Clojure 2021
;; Prepared by Jeremy Taylor, @refset

;; ## 🛠️ Powered by Notespace™
;; This experience was built with https://github.com/scicloj/notespace
;; ..."a notebook experience in your Clojure namespace"
;;
;; Inspired after watching a recent walkthrough by @daslu_ https://youtu.be/uICA2SDa-ws

;; ## 🧑‍💻 To follow along
;; * clone https://github.com/refset/xtdb-workshop-reclojure-22
;; * fire-up your REPL using `clojure -M:notespace`
;; * connect to the REPL from your preferred editor
;;
;; ## 🥱 Alternatively
;; * sit back & watch :)

:_
;; # Setup
;; ## Dependencies

(ns workshop.workshop
  (:require [xtdb.api :as xt]
            [scicloj.notespace.v4.api :as notespace]
            [scicloj.kindly.kind :as kind]
            [workshop.tt-vt :as tt-vt]
            [workshop.xt-universal-relation :as xr]))

;;
;; ## Run Notespace
;; To start (or restart) Notespace, eval the following `restart!` call
(comment
  (notespace/restart!))


;; You should see the following message printed in the REPL:
;; ```
;; Server starting...
;; Ready at port 1903
;; ```
;;
;; You can then open http://localhost:1903 in your browser, and eval the entire `workshop.clj` buffer using your editor

:_
;; # Usage
;; With the `workshop.clj` buffer evaluated, the Notespace browser view is populated with unevaluated forms.
;;
;; Notespace listens to file-save events and to code evaluations in your editor/REPL environment.

;; You can evaluate each form, one by one, to see the result displayed and persisted in the browser view (alongside your regular editor experience).

;; * A file save or an evaluation of a whole file will result in updating the viewed code.

;; * An evaluation of a region will result in updating the last evaluation value, as well as the values of the evaluated forms, if they can be recognized in the code unambiguously.

;; ## Notespace usage tips
;; * eval the whole buffer after calling `(notespace/restart!)`
;; * save the `workshop.clj` file to refresh the view of the ns before eval'ing (notespace watches for saves)
;; * if things get confusing, just call `(notespace/restart!)` again _and_ refresh the browser
;; * sometimes, Notespace does not know how to recognize the evaluated forms unambiguously in your editor's buffer. For example, maybe it appears more than once, maybe the file hasn't been saved since some recent changes, and maybe the evaluated region is not a set of top-level forms
;; * (possible Notespace bug - unconfirmed!) when forms are repeated exactly, I've noticed the eval result gets shown next to the first instance, so just modify the latter forms slightly as a workaround


;;
;; ## Rendering Hiccup
;; Simply add a `^kind/hiccup` metadata tag before the relevant form, e.g.

^kind/hiccup
[:div
 {:style {:margin "1em"}}
 [:svg
  [:rect {:width 30
          :height 30
          :fill "blue"}]]]

;; Notespace also exposes various other built-in rendering capabilities and extension points, e.g.

^kind/hiccup
[:p/sparklinespot
 {:data      (->> #(- (rand) 0.5)
                  (repeatedly 99)
                  (reductions +))
  :svgHeight 50}]

:_
;; # Start XT
;; `xtdb.api/start-node` always takes an options map
;;
;; `{}` = the default in-memory configuration

(def n (xt/start-node {}))

(type n)

;; Is the node alive?

(xt/status n)

;; A node implements the `Closeable` interface, should you ever need `.close` it

(comment
  (.close n))

;;
;; ## FYI
;; * in-process embedded node (although an HTTP server + clj client module is available too)
;; * in-memory by default (i.e. nothing is persisted)
;; * implementation is multi-threaded and uses off-heap memory & caching (i.e. not as lightweight as DataScript)
;; * implicitly a "standalone" node, since its locally provisioned tx-log and doc-store cannot be accessed by other nodes to create a cluster topology

:_
;; # Basics
;; Let's find _everything_ in the database using a basic Datalog query.
;; This requires first creating a `db` snapshot which allows for repeatable reads.
;;
;; A Datalog query is like a set of simultaneous equations - it pattern matches over the logical variables (Clojure symbols), using the data in the database expressed as Entity-Attribute-Value triples, to produce a set of result tuples that satisfy those logic variables
;;
;; In this case the query finds all entities (`e`) where each entity has _some_ `:xt/id` value (`_` is a wildcard):

(xt/q (xt/db n)
      '{:find [e]
        :where [[e :xt/id _]]})

;; (hopefully the result is an empty set, if not you may want to start a new node!)
;;
;; Let's add some data by submit a transaction containing a `put` operation:

(xt/submit-tx n [[::xt/put {:xt/id "foo"
                            :my-number 123}]])

;; Here, the map `{:xt/id "foo" :my-number 123}` is a "document", which represents a version of an entity. The only constraints are that all documents must specify an `:xt/id` and that all fields (Clojure keys) must be Clojure keywords.


;;
;; Note that document field values with either sets or vectors are interpreted as composite values and are automatically decomposed into multiple EAVs (i.e. all document attributes are potentially "cardinality many"). Other complex values types like maps are stored as opaque value "hashes" so deeply nested values inside documents won't be available for efficient lookups or joins.

;;
;; XT is asynchronous by nature, so although we have called `submit-tx` we don't yet whether the transaction has been processed (or committed, if a `match` operation was used). To be sure that this transaction has been processed that the data is ready to be queried, we can use the returned transaction receipt to block the thread and wait until the data has been processed:

(->> [[::xt/put {:xt/id "bar"
                 :my-number 123}]]
     (xt/submit-tx n)
     (xt/await-tx n))

;; Now let's a take a look at the list of entities again:

(do 1 ;; workaround for Notespace eval of identical forms
    (xt/q (xt/db n)
           '{:find [e]
             :where [[e :xt/id _]]}))

;; Note that the set result tuples corresponds to the `:find` vector definition.

;; We can use the `pull` pattern feature to retrieve the full documents again:

(xt/q (xt/db n)
      '{:find [(pull e [*])]
        :where [[e :xt/id _]]})

;; `pull` can be used for deeply nested selections and joins, and follows the "EQL" specification documented at https://edn-query-language.org/

;;  We can also use the `entity` API to retrieve an individual document:

(xt/entity (xt/db n)
           "foo")

:_
;; # Joins
;; To join across documents, all XT requires is that the values and IDs correlate. This is achieved dynamically since XT does not ask the user to define a schema, therefore binary hash comparisons are used to perform joins at runtime across all value types (and XT supports arbitrary value types, thanks to https://github.com/ptaoussanis/nippy).

(xt/q (xt/db n)
      '{:find [e]
        :where [["foo" :my-number mynum]
                [e :my-number mynum]]})

;; Here we have used a literal value `"foo"` in the entity position of the first "triple clause".
;;
;; More importantly, we also introduced a new logic variable, which acts as an implicit join between the first clause and the second clause. This allowed XT to join across the number `123`.
;;
;; Note that both entities are returned.

;;
;; ## How is this possible?

;; XT stores graph-friendly indexes, such that a pair of `E+A->V` and `A+V->E` indexes are combined to make arbitrary navigation symmetrical and efficient.
;;
;; As a crude approximation, there is a _sorted_ Key-Value (KV) index for `E+A->V` that looks like:

[["bar" :my-number 123]
 ["foo" :my-number 123]]

;; And another sorted KV index for `A+V->E` that looks like:

[[:my-number 123 "bar"]
 [:my-number 123 "foo"]]

;;
;; ## FYI
;; * note that XT does not store an index that allows for wildcard attribute lookups, therefore the attribute position in triple clauses only accepts concrete values (Clojure keywords)

:_
;; # Temporality
;; ## Each entity has a timeline of document versions
;; By default we always write a document "now", so we don't have to concern ourselves with time _until we absolutely have to_.
;;
;; First, let's make it slightly more convenient to submit transactions:

(defn submit! [n tx]
  (let [txr (xt/submit-tx n tx)]
    (xt/sync n)
    txr))

;; Note that `sync` will ensure all currently submitted transactions are processed (imagine multiple nodes submitting transactions to the tx-log concurrently)
;;
;; Let's update our `"foo"` document:

(submit! n [[::xt/put {:xt/id "foo"
                       :my-number 123
                       :my-color "red"}]])

;; Note that we always transact in terms of whole documents. This has both some benefits and a some downsides, but is generally simpler than attempting to merge updates in a bitemporal environment without a user-supplied schema :)

;; We can now take a look at the `entity-history` of `"foo"`:

(xt/entity-history (xt/db n) "foo" :asc)

;; Let's try "backfilling" the history of `"foo"` by adding a `start-valid-time` parameter:

(submit! n [[::xt/put {:xt/id "foo"
                       :my-number 123
                       :my-color "red"}
             #inst "2021-12-01"]])

;; Take a look at the `entity-history` again and note the new entry in the timeline using the valid-time ordering.

(do 1 ;; (Notespace workaround, again 🙈)
    (xt/entity-history (xt/db n)
                       "foo"
                       :asc
                       {:with-docs? true}))

;; To correct history, simply submit a document at the same valid-time:

(submit! n [[::xt/put {:xt/id "foo"
                       :my-number 123
                       :my-color "blue"}
             #inst "2021-12-01"]])

;; Again:

(do 2
    (xt/entity-history (xt/db n)
                       "foo"
                       :asc
                       {:with-docs? true}))

;; For auditing, and for global `db` consistency (i.e. repeated querying against a database snapshot), XT also maintains efficient access to the previous entity versions from the perspective of transaction-time. To see this in the `entity-history`, simply provide an extra option to the call:

(xt/entity-history (xt/db n)
                   "foo"
                   :asc
                   {:with-docs? true
                    :with-corrections? true})

;; Note that corrections are returned using a sub-ordering by transaction-time

;;
;; We can also `put` documents into the future:

(submit! n [[::xt/put {:xt/id "foo"
                       :my-number 42
                       :my-color "orange"}
             #inst "2021-12-05"]])

;; Again:

(do 3
    (xt/entity-history (xt/db n)
                       "foo"
                       :asc
                       {:with-docs? true}))

;; Hmm, where is our new version? Try again without limiting our view of "history" (which includes the future!) to "now"

(xt/entity-history (xt/db n #inst "2030-01-01")
                   "foo"
                   :asc
                   {:with-docs? true})

:_
;; # Tt-Vt

;; Since we have two time dimensions for each entity, we can meaningfully visualize the entity versions in a 2D diagram.
;;
;; In response to diagrams seen in academic literature, requests from XT users, and other bitemporal data resources like https://bitemporal.net/generate-bitemporal-intervals/ - a work-in-progress `tt-vt/->tt-vt` function has been created to generate Tt-Vt diagrams as SVG

^kind/hiccup
[:div {:style {:margin "20px"}}
 (->> (xt/entity-history (xt/db n)
                              "foo"
                              :asc
                              {:with-docs? true
                               :with-corrections? true})
           (tt-vt/entity-history->tt-vt 100 100 :my-color))]

:_
;; # Universal Relation

;; How might an XT database look through the lens of a traditional RDBMS?
;;
;; See the REPL for this one

(xr/node->prn-table n)

;; Note that `:xt/nil` is simply padding for the table, representing the absence of data (since `nil` is a valid value).

:_
;; # Storage

;; ...

:_
;; # Graphs

;; Let's pull some data and forms from Learn XTDB Datalog Today https://nextjournal.com/try/learn-xtdb-datalog-today/learn-xtdb-datalog-today

:_
;; # Transactions
;; ## Match Op

;; ...

;; ## Evict Op

;; ...

;; ## TxFns Ops

;; ...

;; ## `with-tx`

;; ...

:_
;; # See Also

;; ## Lots of documentation @ xtdb.com

;; ## Tutorials

;; API overview https://nextjournal.com/xtdb-tutorial
;;
;; Learn XTDB Datalog Today https://nextjournal.com/try/learn-xtdb-datalog-today/learn-xtdb-datalog-today
;;
;; HTTP + JSON https://docs.xtdb.com/tutorials/xtdb-over-http/

;; ## Lucene

;; https://docs.xtdb.com/extensions/full-text-search/

;; ## HTTP + JSON (OpenAPI)

;; https://docs.xtdb.com/extensions/http/

;; ## Java API

;; https://docs.xtdb.com/clients/java/javadoc/1.20.0/

;; ## Advanced Configurations

;; https://docs.xtdb.com/administration/configuring/

;; ## Monitoring & Operations

;; https://docs.xtdb.com/administration/monitoring/

;; https://docs.xtdb.com/administration/checkpointing/

:_
;; # Wrap-up
;; ## 🎉 Congratulations 🎉
;; If you spot any errors or would like to make a suggestion, issues and PRs are welcome!
;; ## 🙏 Have a nice day 🙏

nil
