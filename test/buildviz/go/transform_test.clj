(ns buildviz.go.transform-test
  (:require [buildviz.go.transform :as transform]
            [clojure.test :refer :all]))

(defn- a-job-instance
  ([outcome actual-stage-run] {:outcome outcome
                               :start 100
                               :end 200
                               :actual-stage-run actual-stage-run})
  ([start-time end-time actual-stage-run] {:outcome "pass"
                                           :start start-time
                                           :end end-time
                                           :actual-stage-run actual-stage-run}))

(defn- a-job-instance-with-xml [xml actual-stage-run]
  {:outcome "pass"
   :start 100
   :end 200
   :actual-stage-run actual-stage-run
   :junit-xml xml})

(defn- a-stage-instance [job-instances stage-run]
  {:pipeline-name "my pipeline"
   :pipeline-run "42"
   :stage-name "some stage"
   :stage-run stage-run
   :job-instances job-instances})

(deftest test-gocd-transform
  (testing "outcome"
    (testing "should set outcome to pass for all jobs passing"
      (is (= "pass"
             (:outcome (:build (transform/stage-instances->builds (a-stage-instance [(a-job-instance "pass" "1")
                                                                                     (a-job-instance "pass" "1")] "1")))))))

    (testing "should set outcome to fail for a failing job"
      (is (= "fail"
             (:outcome (:build (transform/stage-instances->builds (a-stage-instance [(a-job-instance "pass" "1")
                                                                                     (a-job-instance "fail" "1")] "1")))))))

    (testing "should set outcome to pass after re-running a a now passing job"
      (is (= "pass"
             (:outcome (:build (transform/stage-instances->builds (a-stage-instance [(a-job-instance "pass" "2")
                                                                                     (a-job-instance "pass" "1")] "2")))))))

    (testing "should correctly report outcome of stage run, where one failing job was re-run, while one was not (issue #17)"
      (is (= "fail"
             (:outcome (:build (transform/stage-instances->builds (a-stage-instance [(a-job-instance "pass" "2")
                                                                                     (a-job-instance "fail" "1")] "2"))))))))

  (testing "build time"
    (testing "should take lowest start time"
      (is (= 100
             (:start (:build (transform/stage-instances->builds (a-stage-instance [(a-job-instance 200 400 "1")
                                                                                   (a-job-instance 100 300 "1")] "1")))))))

    (testing "should take highest end time"
      (is (= 400
             (:end (:build (transform/stage-instances->builds (a-stage-instance [(a-job-instance 200 400 "1")
                                                                                 (a-job-instance 100 300 "1")] "1")))))))

    (testing "should only consider times of re-run jobs"
      (let [aggregated-job (:build (transform/stage-instances->builds (a-stage-instance [(a-job-instance 200 300 "2")
                                                                                         (a-job-instance 100 400 "1")] "2")))]
        (is (= 200
               (:start aggregated-job)))
        (is (= 300
               (:end aggregated-job))))))

  (testing "xml"
    (testing "should merge JUnit XML lists"
      (is (= ["testsuite 1" "second suite" "another testsuite"]
             (:junit-xml (transform/stage-instances->builds (a-stage-instance [(a-job-instance-with-xml ["testsuite 1" "second suite"] "1")
                                                                               (a-job-instance-with-xml ["another testsuite"] "1")] "1"))))))))
