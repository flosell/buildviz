(ns buildviz.analyse.testsuites
  (:require [buildviz.analyse
             [builds :as jobinfo]
             [testsuite-transform :as transform]]
            [buildviz.data.tests-schema :as tests-schema]
            [buildviz.util.math :as math]))

(defn- avg-with-nil [values]
  (when-let [non-nil-values (seq (remove nil? values))]
    (math/avg non-nil-values)))

(defn average-runtime [testcases]
  (if-let [average-runtime (->> testcases
                                (map #(transform/testcase->data % :runtime))
                                avg-with-nil)]
    {:average-runtime average-runtime}
    {}))

(defn aggregate-testcase-runs [testcases]
  (let [failed-count (->> testcases
                          (map transform/testcase->data)
                          (remove tests-schema/is-ok?)
                          count)]
    (assoc (average-runtime testcases)
           :failed-count failed-count)))

(defn- aggregated-info-by-testcase [test-runs]
  (->> (transform/test-runs->testcase-list test-runs)
       (group-by transform/testcase->id)
       (map (transform/testcase-with-data
             (fn [testcase-group] (aggregate-testcase-runs testcase-group))))))


(defn aggregate-testcase-info [test-runs]
  (transform/testcase-list->testsuite-tree (aggregated-info-by-testcase test-runs)))

(defn aggregate-testcase-info-as-list [test-runs]
  (map transform/testcase->map (aggregated-info-by-testcase test-runs)))


(defn- average-runs [unrolled-testcases]
  (->> unrolled-testcases
       (group-by transform/testcase->id)
       (map (transform/testcase-with-data
             (fn [testcase-group] (average-runtime testcase-group))))))

(defn- skipped-missing-runtime? [testcase]
  (and (nil? (:runtime testcase))
       (tests-schema/is-skipped? testcase)))

(defn- accumulated-runtime [testcases]
  (let [runtimes (->> testcases
                      (remove skipped-missing-runtime?)
                      (map :runtime))]
    (when (not-any? nil? runtimes)
      (apply + runtimes))))

(defn- accumulate-runtime-by-class-for-testcases [testcases]
  (map (fn [[classname testcases]]
         {:name classname
          :runtime (accumulated-runtime testcases)})
       (group-by :classname testcases)))

(defn- accumulate-runtime-by-class [testsuite]
  (let [nested-suites (filter :children (:children testsuite))
        testcases (remove :children (:children testsuite))]
    (assoc testsuite
           :children
           (concat (map accumulate-runtime-by-class nested-suites)
                   (accumulate-runtime-by-class-for-testcases testcases)))))

(defn- average-runtimes-by-testclass [test-runs]
  (->> test-runs
       (map #(map accumulate-runtime-by-class %))
       transform/test-runs->testcase-list
       average-runs))


(defn average-testclass-runtime [test-runs]
  (transform/testcase-list->testsuite-tree (average-runtimes-by-testclass test-runs)))

(defn average-testclass-runtime-as-list [test-runs]
  (map transform/testclass->map
       (average-runtimes-by-testclass test-runs)))


(defn- find-flaky-build-candidates [builds]
  (->> (seq builds)
       (map (fn [[build-id build-data]]
              (assoc build-data :id build-id)))
       (jobinfo/builds-grouped-by-same-inputs)
       (filter #(< 1 (count %)))))

(defn- unrolled-testcases [build test-results-func]
  (->> (test-results-func (:id build))
       transform/unroll-testsuites
       (map (transform/testcase-with-data
             (fn [testcase] {:build build
                             :ok? (tests-schema/is-ok? testcase)})))))

(defn- flaky-testcases-for-builds [builds-with-same-input test-results-func]
  (->> builds-with-same-input
       (mapcat (fn [build]
                 (unrolled-testcases build test-results-func)))
       (group-by transform/testcase->id)
       vals
       (map (fn [testcases]
              (group-by #(transform/testcase->data % :ok?)
                        testcases)))
       (filter (fn [testcase-result-groups]
                 (< 1 (count testcase-result-groups))))
       (mapcat (fn [testcase-result-groups]
              (get testcase-result-groups false)))))

(defn- flaky-testcase-summary [unrolled-testcases]
  (let [flaky-builds (map #(transform/testcase->data % :build) unrolled-testcases)
        last-build (apply max-key :start flaky-builds)]
    {:latest-build-id (:id last-build)
     :latest-failure (:start last-build)
     :flaky-count (count unrolled-testcases)}))

(defn- find-flaky-testcases [builds test-results-func]
  (->> builds
       find-flaky-build-candidates
       (mapcat #(flaky-testcases-for-builds % test-results-func))
       (group-by transform/testcase->id)
       (map (transform/testcase-with-data
             (fn [testcases] (flaky-testcase-summary testcases))))))

(defn flaky-testcases [builds test-results-func]
  (transform/testcase-list->testsuite-tree (find-flaky-testcases builds test-results-func)))

(defn flaky-testcases-as-list [builds test-results-func]
  (map transform/testcase->map (find-flaky-testcases builds test-results-func)))
