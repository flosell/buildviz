(ns buildviz.junit-xml
  (:require [clojure.xml :as xml]))

;; Parsing is following schema documented in http://llg.cubic.org/docs/junit/

(defn- is-failure? [testcase-elem]
  (some #(= :failure (:tag %))
        (:content testcase-elem)))

(defn- is-error? [testcase-elem]
  (some #(= :error (:tag %))
        (:content testcase-elem)))

(defn- item-name [elem]
  (:name (:attrs elem)))

(defn- parse-runtime [testcase-elem]
  (if-let [time (:time (:attrs testcase-elem))]
    (Math/round (* 1000 (Float/parseFloat time)))))

(defn- parse-status [testcase-elem]
  (if (is-failure? testcase-elem)
    :fail
    (if (is-error? testcase-elem)
      :error
      :pass)))

(defn- add-runtime [testcase testcase-elem]
  (if-let [runtime (parse-runtime testcase-elem)]
    (assoc testcase :runtime runtime)
    testcase))

(defn- add-class [testcase testcase-elem]
  (if-let [classname (:classname (:attrs testcase-elem))]
    (assoc testcase :classname classname)
    testcase))

(defn- testcase [testcase-elem]
  (-> {:name (item-name testcase-elem)
       :status (parse-status testcase-elem)}
      (add-runtime testcase-elem)
      (add-class testcase-elem)))

(declare parse-testsuite)

(defn- properties? [elem]
  (= :properties (:tag elem)))

(defn- testsuite? [elem]
  (= :testsuite (:tag elem)))

(defn- ignore-properties [children]
  (filter (complement properties?) children))

(defn- testsuite [testsuite-elem]
  {:name (item-name testsuite-elem)
   :children (map parse-testsuite
                  (ignore-properties (:content testsuite-elem)))})

(defn- parse-testsuite [elem]
  (if (testsuite? elem)
    (testsuite elem)
    (testcase elem)))

(defn parse-testsuites [junit-xml-result]
  (let [root (xml/parse (java.io.ByteArrayInputStream. (.getBytes junit-xml-result)))]
    (if (= :testsuites (:tag root))
      (map parse-testsuite
           (:content root))
      (list (parse-testsuite root)))))