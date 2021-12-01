(ns workshop.tt-vt
  (:require [clojure.string :as str]))

(defn hiccup [v]
  (cond (vector? v)
        (let [tag (first v)
              attrs (second v)
              attrs (when (map? attrs) attrs)
              elts (if attrs (nnext v) (next v))
              tag-name (name tag)]
          (str "<" tag-name (hiccup attrs) ">" (hiccup elts) "</" tag-name ">\n"))
        (map? v)
        (str/join ""
                  (map (fn [[k v]]
                         (str " " (name k) "=\"" v "\"")) v))
        (seq? v)
        (str/join " " (map hiccup v))
        :else (str v)))

(defn svg [attributes & content]
  (str "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>\n"
       "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\"
         \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n"
       (hiccup
        (apply vector :svg
               (conj attributes
                     ["xmlns" "http://www.w3.org/2000/svg"]
                     ["xmlns:xlink" "http://www.w3.org/1999/xlink"]
                     ["version" "1.1"])
               content))))


(defn iso-timestamp [^java.util.Date d]
  (let [tz (java.util.TimeZone/getTimeZone "UTC")
        df (new java.text.SimpleDateFormat "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")]
    (.setTimeZone df tz)
    (.format df d)))

;; WARNING: doesn't currently use tx-id to tiebreak tx-time, so may generate visual errors where different txes happen in the same microsecond
(defn normalize-entries [w h xs]
  (let [clj-entry (inst? (:xtdb.api/tx-time (first xs)))
        xs (if clj-entry
             (map (fn xt-history->simple-history [x]
                    (assoc x
                           :txTime (iso-timestamp (:xtdb.api/tx-time x))
                           :validTime (iso-timestamp (:xtdb.api/valid-time x))
                           :doc (:xtdb.api/doc x))) xs)
               xs)
        xs (sort-by (juxt :txTime :validTime) xs) ;; draw order needs :asc tt-vt
        ;;_ (prn xs)
        tts (sort (distinct (map :txTime xs)))
        tts-count (count tts)
        tts-map (zipmap tts (range))
        wm (/ w tts-count 1.0)
        vts (sort (distinct (map :validTime xs)))
        vts-count (count vts)
        vts-map (zipmap vts (range))
        hm (/ h vts-count 1.0)]
    (for [{:keys [validTime txTime] :as x} xs]
      [(* (tts-map txTime) wm)
       0
       (* (- tts-count (tts-map txTime)) wm)
       (* (- vts-count (vts-map validTime)) hm)
       x])))

(defn history->hiccup [width height color-att entries]
  (->> entries
       (normalize-entries width height)
       (map (fn [[x y w h e]]
              [:g
               [:rect {:x x
                          :y y
                          :width w
                          :height h
                       :fill (or (color-att (:doc e))
                                 (str "#" (format "%06x" (rand-int 16rFFFFFF)))
                                 #_(str "#" (.toString (rand-int 16rFFFFFF) 16)))}]
               [:text {:fill "black"
                       :x (+ x (* 0.05 height))
                       :y (- h (* 0.05 height))}
                (str (:fill (:doc e)))]
               ;; TODO extract these so they only get rendered once
               [:text {:fill "black"
                       :x (+ width (* 0.05 height))
                       :y (- h (* 0.05 height))}
                (str (:validTime e))]
               [:g {:transform (str "translate("
                                    (+ x (* 0.05 height))
                                    ","
                                    (+ height (* 0.05 height))
                                    ")")}
                [:text {:fill "black"
                        :transform-box "fill-box"
                        :transform "rotate(90)"}
                 (str (:txTime e))]]]))
       (apply conj [:g])))




(defn entity-history->tt-vt [width height color-att entity-history]
  [:svg
   (history->hiccup
    (/ width 2 1.0)
    (/ height 2 1.0)
    color-att
    entity-history)])

#_(clojure.walk/keywordize-keys (js->clj (.parse js/JSON (js/prompt))))

(def sample [{:txTime "0"
              :validTime "0"
              :doc {:xt/id :foo
                    :fill "blue"}}
             {:txTime "0"
              :validTime "1"
              :doc {:xt/id :foo
                    :fill "pink"}}
             {:txTime "1"
              :validTime "2"
              :doc {:xt/id :foo
                    :fill "orange"}}
             {:txTime "0"
              :validTime "3"
              :doc {:xt/id :foo
                    :fill "red"}}])

(declare sample2)

^scicloj.kindly.kind/hiccup
[:svg
 (history->hiccup
  (/ 200 2 1.0)
  (/ 200 2 1.0)
  :fill
  sample)]

(def sample2 [{:txTime "2019-01-01"
              :validTime "2019-01-05"
              :doc {}}
             {:txTime "2019-02-09"
              :validTime "2019-01-05"
              :doc {}}
             {:txTime "2019-02-09"
              :validTime "2019-01-10"
              :doc {}}
             {:txTime "2019-02-09"
              :validTime "2019-02-08"
              :doc {}}
             {:txTime "2019-02-10"
              :validTime "2019-02-08"
              :doc {}}
             {:txTime "2019-02-10"
              :validTime "2019-02-15"
              :doc {}}
             {:txTime "2019-02-10"
              :validTime "2019-02-17"
              :doc {}}
             {:txTime "2019-02-12"
              :validTime "2019-02-15"
              :doc {}}
             {:txTime "2019-03-02"
              :validTime "2019-01-10"
              :doc {}}
             {:txTime "2019-04-01"
              :validTime "2019-01-10"
              :doc {}}
             {:txTime "2019-04-01"
              :validTime "2019-04-02"
              :doc {}}])
