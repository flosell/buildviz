(ns buildviz.controllers.pipeline-runtime-test
  (:require [buildviz.test-utils :refer :all]
            [clj-time
             [coerce :as tc]
             [core :as t]]
            [clojure
             [string :as str]
             [test :refer :all]]))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))
(def a-day (* 24 60 60 1000))

(deftest PipelineRuntimeSummary
  (testing "GET to /pipelineruntime"
    ;; GET should return 200
    (is (= 200
           (:status (get-request (the-app) "/pipelineruntime"))))

    ;; GET should return empty list by default
    (is (= "date\n"
           (:body (get-request (the-app) "/pipelineruntime"))))

    ;; GET should return the average runtime for each job as well as total
    (let [app (the-app)]
      (a-build app "aBuild" 1, {:start a-timestamp :end (+ a-timestamp 1000)})
      (a-build app "aBuild" 2, {:start (+ a-timestamp 2000) :end (+ a-timestamp 4001)})
      (a-build app "aBuild" 3, {:start (+ a-timestamp a-day) :end (+ a-timestamp a-day 4000)})
      (a-build app "anotherBuild" 1, {:start a-timestamp :end (+ a-timestamp 4000)})
      (a-build app "buildWithoutTimestamps" 1, {:outcome "pass"})
      (is (= (str/join "\n" ["date,aBuild,anotherBuild"
                         (format "1986-10-14,%.8f,%.8f" 0.00001737 0.00004630)
                         (format "1986-10-15,%.8f," 0.00004630)
                         ""])
             (:body (get-request app "/pipelineruntime"))))))

  (testing "should respect 'from' filter"
    (let [app (the-app {"aBuild" {1 {:start (- a-timestamp a-day), :end (- a-timestamp a-day)}}
                        "anotherBuild" {1 {:start a-timestamp :end a-timestamp}}}
                       {})]
      (is (= (str/join "\n" ["date,anotherBuild"
                         (format "1986-10-14,%.8f" 0.0)
                         ""])
             (:body (get-request app "/pipelineruntime" {"from" a-timestamp})))))))
