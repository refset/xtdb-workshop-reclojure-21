(ns workshop.xt-universal-relation
  (:require [xtdb.api :as xt]
            [clojure.string :as str]))

(defn node->prn-table [n]
  (let [es (sort-by (juxt ::xt/tx-id ::xt/valid-time) (reduce (fn get-all-e-versions [p e]
                                                                (apply conj p (xt/entity-history (xt/db n) e :asc {:with-docs? true :with-corrections? true})))
                                                              []
                                                              (into #{}
                                                                    (with-open [l (xt/open-tx-log n nil true)]
                                                                      (doall (reduce (fn reduce-txs [p t]
                                                                                       (into p (map (comp :eid xtdb.tx.conform/conform-tx-op)
                                                                                                    (::xt/tx-ops t))))

                                                                                     []
                                                                                     (iterator-seq l)))))))
        atts (sort (keys (dissoc (xt/attribute-stats n) :xt/id)))
        expanded-es (map (fn apply-atts-to-e [e]
                           (apply conj
                                  [(::xt/tx-id e) (::xt/tx-time e) (:xt/id (::xt/doc e)) (::xt/valid-time e)]
                                  (map (fn lookup-att [a]
                                         (get (dissoc (::xt/doc e) :xt/id) a :xt/nil))
                                       atts)))
                         es)
        table (into [(into [::xt/tx-id ::xt/tx-time-start :xt/id ::xt/valid-time-start] atts)]
                    expanded-es)]
    (clojure.pprint/print-table (first table) (map #(zipmap (first table) %) (rest table)))
    ))

(let [n (xt/start-node {})
      _ (->> [[::xt/put {:xt/id :foo :a 1}]]
             (xt/submit-tx n)
             (xt/await-tx n))
      _ (->> [[::xt/put {:xt/id :bar :b 2}]]
             (xt/submit-tx n)
             (xt/await-tx n))
      _ (->> [[::xt/put {:xt/id :bar :c 3}]]
             (xt/submit-tx n)
             (xt/await-tx n))]
  (node->prn-table n))
