(ns buildviz.data.results-test
  (:require [buildviz.data.results :as results]
            [clj-time.core :as t]
            [clojure.test :refer :all]))

(defn- dummy-store [_ _ _])
(defn- dummy-load-tests [_ _])

(def a-date-time (t/date-time 1986 10 14 4 3 27 456))

(deftest test-build-results-last-modified
  (testing "should return last modified datetime"
    (let [build-results (results/build-results {} dummy-load-tests dummy-store dummy-store)]
      (is (t/before? a-date-time
                     (results/last-modified build-results)))))

  (testing "should update last modified datetime on change"
    (testing "of builds"
      (let [build-results (results/build-results a-date-time {} dummy-load-tests dummy-store dummy-store)
            last-modified (results/last-modified build-results)]
        (results/set-build! build-results "aJob" "aBuild" {})
        (is (t/before? last-modified
                       (results/last-modified build-results)))))
    (testing "of tests"
      (let [build-results (results/build-results a-date-time {} dummy-load-tests dummy-store dummy-store)
            last-modified (results/last-modified build-results)]
        (results/set-tests! build-results "aJob" "aBuild" "<xml>")
        (is (t/before? last-modified
                       (results/last-modified build-results)))))))

(deftest test-build-results-builds
  (testing "should return builds starting with given timestamp"
    (let [build-results (results/build-results {"aJob" {"1" {:start 42}
                                                        "2" {:start 100}
                                                        "4" {:start 200}}
                                                "anotherJob" {"3" {:start 150}}}
                                               dummy-load-tests
                                               dummy-store
                                               dummy-store)]
      (is (= '({:start 100} {:start 200})
             (results/builds build-results "aJob" 100))))))

(deftest test-build-results-all-builds
  (testing "should return builds of all jobs"
    (let [build-results (results/build-results {"someJob" {"1" {:start 1}
                                                           "2" {:start 2}}
                                                "otherJob" {"4" {:start 4}
                                                            "6" {:start 5}}}
                                               dummy-load-tests
                                               dummy-store
                                               dummy-store)]
      (is (= '({:start 1 :job "someJob" :build-id "1"}
               {:start 2 :job "someJob" :build-id "2"}
               {:start 4 :job "otherJob" :build-id "4"}
               {:start 5 :job "otherJob" :build-id "6"})
             (results/all-builds build-results)))))

  (testing "should filter by timestamp"
    (let [build-results (results/build-results {"someJob" {"1" {:start 10}
                                                           "4" {:start 40}}
                                                "otherJob" {"2" {:start 20}
                                                            "5" {:start 50}}}
                                               dummy-load-tests
                                               dummy-store
                                               dummy-store)]
      (is (= '({:start 40 :job "someJob" :build-id "4"}
               {:start 50 :job "otherJob" :build-id "5"})
             (results/all-builds build-results 30))))))

(deftest test-build-results-tests
   (testing "should return tests for a build"
     (let [load-tests (fn [job-name build-id]
                        (get-in {"aJob" {"1" "<thexml>"}}
                                [job-name build-id]))
           build-results (results/build-results {} load-tests dummy-store dummy-store)]
       (is (= "<thexml>"
              (results/tests build-results "aJob" "1"))))))

(deftest test-build-results-chonological-tests
   (testing "should return all tests for existing builds"
     (let [load-tests (fn [job-name build-id]
                        (get-in {"aJob" {"1" "<thexml>"
                                         "2" "<morexml>"}
                                 "anotherJob" {"3" "<somemorexml>"}}
                                [job-name build-id]))
           build-results (results/build-results {"aJob" {"1" {:start 0}
                                                         "2" {:start 1}
                                                         "4" {:start 3}}
                                                 "anotherJob" {"3" {:start 2}}}
                                                load-tests
                                                dummy-store
                                                dummy-store)]
       (is (= '("<thexml>" "<morexml>")
              (results/chronological-tests build-results "aJob" nil)))))

   (testing "should return all tests starting with given timestamp"
     (let [load-tests (fn [job-name build-id]
                        (get-in {"aJob" {"1" "<thexml>"
                                         "2" "<morexml>"
                                         "4" "<evenmorexml>"}
                                 "anotherJob" {"3" "<somemorexml>"}}
                                [job-name build-id]))
           build-results (results/build-results {"aJob" {"1" {:start 42}
                                                         "2" {:start 100}
                                                         "4" {:start 200}}
                                                 "anotherJob" {"3" {:start 150}}}
                                                load-tests
                                                dummy-store
                                                dummy-store)]
       (is (= '("<morexml>" "<evenmorexml>")
              (results/chronological-tests build-results "aJob" 100))))))

(deftest test-build-results-set-build!
   (testing "should persist a build"
     (let [fake-storage (atom {})
           store-build! (fn [job-name build-id build]
                          (swap! fake-storage assoc-in [job-name build-id] build))
           build-results (results/build-results {}
                                                dummy-load-tests
                                                store-build!
                                                dummy-store)]
       (results/set-build! build-results "someJob" "42" {:start 4711})
       (is (= {:start 4711}
              (get-in @fake-storage ["someJob" "42"]))))))

(deftest test-build-results-set-tests!
  (testing "should persist testresults"
     (let [fake-storage (atom {})
           store-tests! (fn [job-name build-id xml]
                          (swap! fake-storage assoc-in [job-name build-id] xml))
           build-results (results/build-results {}
                                                dummy-load-tests
                                                dummy-store
                                                store-tests!)]
       (results/set-tests! build-results "anotherJob" "21" "<the-xml/>")
       (is (= "<the-xml/>"
              (get-in @fake-storage ["anotherJob" "21"]))))))
