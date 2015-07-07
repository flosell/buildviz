(ns buildviz.handler-test
  (:use clojure.test
        ring.mock.request
        buildviz.handler
        [clojure.string :only (join)])
  (:require [cheshire.core :as json]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))

(defn- reset-app! []
  (reset! builds {})
  (reset! test-results {}))

(defn each-fixture [f]
  (reset-app!)
  (f))

(use-fixtures :each each-fixture)

(defn a-build [jobName, buildNr, content]
  (app (-> (request :put
                    (format "/builds/%s/%s" jobName buildNr))
           (body (json/generate-string content))
           (content-type "application/json"))))

(defn some-test-results [job-name build-no content]
  (app (-> (request :put (format "/builds/%s/%s/testresults" job-name build-no))
           (body content)
           (content-type "application/xml"))))

(deftest Storage
  (testing "PUT to /builds/:job/:build"
    ;; PUT should return 200
    (let [response (app (request :put "/builds/mybuild/1"))]
      (is (= (:status response) 200)))

    ; PUT should return content
    (let [response (app (-> (request :put
                                     "/builds/abuild/1")
                            (body (json/generate-string {:start 42 :end 43}))
                            (content-type "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43}))))

  (testing "GET to /builds/:job/:build"
    ;; GET should return 200
    (a-build "anotherBuild" 1 {:start 42 :end 43})
    (let [response (app (request :get "/builds/anotherBuild/1"))]
      (is (= (:status response) 200)))

    ;; GET should return content stored by PUT
    (a-build "yetAnotherBuild" 1, {:start 42 :end 43})
    (let [response (app (request :get "/builds/yetAnotherBuild/1"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43})))

    ;; GET should return 404 if job not found
    (let [response (app (request :get "/builds/unknownBuild/10"))]
      (is (= (:status response) 404)))

    ;; GET should return 404 if build not found
    (a-build "anExistingBuild" 1, {:start 42 :end 43})
    (let [response (app (request :get "/builds/anExistingBuild/2"))]
      (is (= (:status response) 404)))

    ;; Different jobs should not interfere with each other
    (a-build "someBuild" 1, {:start 42 :end 43})
    (let [response (app (request :get "/builds/totallyUnrelatedBuild/1"))
          resp-data (json/parse-string (:body response))]
      (is (= (:status response) 404)))))


(deftest JUnitStorage
  (testing "PUT to /builds/:job/:build/testresults"
    (is (= 204 (:status (app (-> (request :put "/builds/mybuild/1/testresults")
                                 (body "<testsuites></testsuites>")
                                 (content-type "application/xml")))))))
  (testing "GET to /builds/:job/:build/testresults"
    (some-test-results "abuild" "42" "bla bla")
    (let [response (app (request :get "/builds/abuild/42/testresults"))]
      (is (= 200 (:status response))))

    (some-test-results "anotherBuild" "2" "some content")
    (let [response (app (request :get "/builds/anotherBuild/2/testresults"))]
      (is (= "some content" (:body response))))

    (some-test-results "anotherBuild" "2" "some content")
    (let [response (app (request :get "/builds/anotherBuild/2/testresults"))]
      (is (= {"Content-Type" "application/xml;charset=UTF-8"} (:headers response))))

    (let [response (app (request :get "/builds/missingJob/1/testresults"))]
      (is (= 404 (:status response))))

    (some-test-results "jobMissingABuild" "1" "something")
    (let [response (app (request :get "/builds/jobMissingABuild/2/testresults"))]
      (is (= 404 (:status response))))

    (some-test-results "job" "1" "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"suite\"><testcase name=\"case\"></testcase></testsuite>")
    (let [response (app (-> (request :get "/builds/job/1/testresults")
                            (header :accept "application/json")))]
      (is (= [{"name" "suite"
               "children" [{"name" "case"
                           "status" "pass"}]}] (json/parse-string (:body response)))))
  ))


(deftest JobsSummary
  (testing "GET to /jobs"
    ; GET should return 200
    (let [response (app (request :get "/jobs"))]
      (is (= (:status response) 200)))

    ;; GET should return empty list
    (let [response (app (-> (request :get "/jobs")
                            (header :accept "text/plain")))]
      (is (= (:body response)
             "job,averageRuntime,totalCount,failedCount,flakyCount\n")))

    ;; GET should return job summary
    (reset-app!)
    (a-build "someBuild" 1, {:start 10 :end 20 :outcome "pass" :inputs {:id 42 :revision "dat_revision"}})
    (a-build "someBuild" 2, {:start 30 :end 60 :outcome "fail" :inputs {:id 42 :revision "dat_revision"}})
    (a-build "someBuild" 3, {:start 70 :end 90 :outcome "fail" :inputs {:id 42 :revision "other_revision"}})
    (a-build "someBuild" 4, {:start 100 :end 120 :outcome "pass" :inputs {:id 42 :revision "yet_another_revision"}})
    (let [response (app (-> (request :get "/jobs")
                            (header :accept "text/plain")))]
      (is (= (:body response)
             (join ["job,averageRuntime,totalCount,failedCount,flakyCount\n"
                    "someBuild,20,4,2,1\n"]))))

    ;; GET should return empty map for json by default
    (reset-app!)
    (let [response (app (-> (request :get "/jobs")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {})))

    ;; GET should return job summary
    (reset-app!)
    (a-build "someBuild" 1, {:start 42 :end 43})
    (a-build "anotherBuild" 1, {:start 10 :end 12})
    (let [response (app (-> (request :get "/jobs")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"someBuild" {"averageRuntime" 1 "totalCount" 1}
                        "anotherBuild" {"averageRuntime" 2 "totalCount" 1}})))

    ;; GET should return total build count
    (reset-app!)
    (a-build "runOnce" 1, {})
    (a-build "runTwice" 1, {})
    (a-build "runTwice" 2, {})
    (let [response (app (-> (request :get "/jobs")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"runTwice" {"totalCount" 2}
                        "runOnce" {"totalCount" 1}})))

    ;; GET should return failed build count
    (reset-app!)
    (a-build "flakyBuild" 1, {:outcome "pass"})
    (a-build "flakyBuild" 2, {:outcome "fail"})
    (a-build "brokenBuild" 1, {:outcome "fail"})
    (let [response (app (-> (request :get "/jobs")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"flakyBuild" {"failedCount" 1 "totalCount" 2 "flakyCount" 0}
                        "brokenBuild" {"failedCount" 1 "totalCount" 1 "flakyCount" 0}})))

    ;; GET should return error build count
    (reset-app!)
    (a-build "goodBuild" 1, {:outcome "pass"})
    (let [response (app (-> (request :get "/jobs")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"goodBuild" {"failedCount" 0 "totalCount" 1 "flakyCount" 0}})))

    ;; GET should return a flaky build count
    (reset-app!)
    (a-build "flakyBuild" 1, {:outcome "pass" :inputs {:id 42 :revision "dat_revision"}})
    (a-build "flakyBuild" 2, {:outcome "fail" :inputs {:id 42 :revision "dat_revision"}})
    (let [response (app (-> (request :get "/jobs")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"flakyBuild" {"failedCount" 1 "totalCount" 2 "flakyCount" 1}})))
    ))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))

(deftest FailPhases
  (testing "GET to /failphases"
    ;; GET should return 200
    (let [response (app (request :get "/failphases"))]
      (is (= 200 (:status response))))

    ;; GET should return empty list by default
    (reset-app!)
    (let [response (app (request :get "/failphases"))
          resp-data (:body response)]
      (is (= "start,end,culprits\n" resp-data)))

    ;; GET should return fail phases
    (reset-app!)
    (a-build "badBuild" 1, {:end a-timestamp :outcome "fail"})
    (a-build "anotherBuild" 1, {:end (+ a-timestamp 10000) :outcome "fail"})
    (a-build "anotherBuild" 2, {:end (+ a-timestamp 20000) :outcome "pass"})
    (a-build "badBuild" 2, {:end (+ a-timestamp 30000) :outcome "pass"})
    (let [response (app (request :get "/failphases"))
          resp-data (:body response)]
      (is (= "start,end,culprits\n1986-10-14 04:03:27,1986-10-14 04:03:57,anotherBuild|badBuild\n" resp-data)))

    ;; GET should return empty list by default as JSON
    (reset-app!)
    (let [response (app (-> (request :get "/failphases")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= [] resp-data)))

    ;; GET should return fail phases as JSON
    (reset-app!)
    (a-build "badBuild" 1, {:end 42 :outcome "fail"})
    (a-build "badBuild" 2, {:end 80 :outcome "pass"})
    (let [response (app (-> (request :get "/failphases")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= [{"start" 42 "end" 80 "culprits" ["badBuild"]}] resp-data)))
    ))

(deftest FailuresSummary
  (testing "GET to /failures"
    ;; GET should return 200
    (let [response (app (request :get "/failures"))]
      (is (= 200 (:status response))))

    ;; GET should return empty map by default
    (let [response (app (request :get "/failures"))
          resp-data (json/parse-string (:body response))]
      (is (= {} resp-data)))

    ;; GET should return list of failing builds
    (reset-app!)
    (a-build "failingBuild" 1, {:outcome "fail"})
    (a-build "anotherFailingBuild" 1, {:outcome "fail"})
    (let [response (app (request :get "/failures"))
          resp-data (json/parse-string (:body response))]
      (is (= {"anotherFailingBuild" {"failedCount" 1} "failingBuild" {"failedCount" 1}} resp-data)))

    ;; GET should include a list of failing test cases
    (reset-app!)
    (a-build "failingBuild" 1, {:outcome "fail"})
    (some-test-results "failingBuild" "1" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\"><failure/></testcase></testsuite></testsuites>")
    (let [response (app (request :get "/failures"))
          resp-data (json/parse-string (:body response))]
      (is (= {"failingBuild" {"failedCount" 1 "children" [{"name" "a suite"
                                                           "children" [{"name" "a test"
                                                                        "failedCount" 1}]}]}}
             resp-data)))
    ))

(deftest TestsuitesSummary
  (testing "GET to /testsuites"
    ;; GET should return 200
    (let [response (app (-> (request :get "/testsuites")
                            (header :accept "application/json")))]
      (is (= 200 (:status response))))

    ;; GET should return empty map by default
    (let [response (app (-> (request :get "/testsuites")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= {} resp-data)))

    ;; GET should include a list of builds with test cases
    (reset-app!)
    (a-build "aBuild" 1, {})
    (some-test-results "aBuild" "1" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\" time=\"10\"></testcase></testsuite></testsuites>")
(a-build "aBuild" 2, {})
    (some-test-results "aBuild" "2" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\" time=\"30\"></testcase></testsuite></testsuites>")
    (let [response (app (-> (request :get "/testsuites")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= {"aBuild" {"children" [{"name" "a suite"
                                     "children" [{"name" "a test"
                                                  "averageRuntime" 20000}]}]}}
             resp-data)))

    ;; GET should return CSV by default
    (reset-app!)
    (a-build "aBuild" 1, {})
    (some-test-results "aBuild" "1" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a,test\" classname=\"a class\" time=\"10\"></testcase></testsuite></testsuites>")
    (let [response (app (request :get "/testsuites"))]
      (is (= (:body response)
             (join ["averageRuntime,job,testsuite,classname,name\n"
                    "10000,aBuild,a suite,a class,\"a,test\"\n"]))))

    ;; GET should handle nested testsuites in CSV
    (reset-app!)
    (a-build "aBuild" 1, {})
    (some-test-results "aBuild" "1" "<testsuites><testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"a,test\" classname=\"a class\" time=\"10\"></testcase></testsuite></testsuite></testsuites>")
    (let [response (app (-> (request :get "/testsuites")
                            (header :accept "text/plain")))]
      (is (= (:body response)
             (join ["averageRuntime,job,testsuite,classname,name\n"
                    "10000,aBuild,a suite: nested suite,a class,\"a,test\"\n"]))))

    ;; GET should not include builds without test cases
    (reset-app!)
    (a-build "aBuild" 1, {})
    (let [response (app (-> (request :get "/testsuites")
                            (header :accept "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= {} resp-data)))
    ))

(deftest EntryPoint
  (testing "GET to /"
    (let [response (app (request :get "/"))]
      (is (= 302 (:status response))))

    (let [response (app (request :get "/"))]
      (is (= "/index.html" (get (:headers response) "Location"))))))
