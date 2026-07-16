(ns beverageretailops.governor-test
  "Pure unit tests of `beverageretailops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [beverageretailops.advisor :as adv]
            [beverageretailops.governor :as gov]
            [beverageretailops.store :as store]))

(def store-1 {:store-id "store-1" :name "Downtown Wine & Spirits" :registered? true :verified? true})
(def store-3 {:store-id "store-3" :name "Harbor Beverage Outlet" :registered? true :verified? false})

(defn- clean-proposal [op store-id]
  {:op op :store-id store-id :summary "s" :rationale "routine retail coordination"
   :cites [store-id] :effect :propose :value {} :confidence 0.85})

(deftest store-unregistered-is-hard
  (testing "no store record at all -> HARD hold"
    (let [s (store/mem-store {"store-1" store-1})
          verdict (gov/check {} nil (clean-proposal :log-sales-record "unknown-store") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:store-unverified} (map :rule (:violations verdict)))))))

(deftest store-unverified-is-hard
  (testing "store registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"store-3" store-3})
          verdict (gov/check {} nil (clean-proposal :log-sales-record "store-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:store-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"store-1" store-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-staffing-operation "store-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"store-1" store-1})
          verdict (gov/check {} nil (clean-proposal :finalize-age-verification-override "store-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest age-verification-override-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches finalizing an age-verification override is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"store-1" store-1})
          poisoned (assoc (clean-proposal :log-sales-record "store-1")
                          :rationale "finalized the age verification override for this customer"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest responsible-service-override-content-is-hard
  (testing "a proposal touching a responsible-service-of-alcohol override is HARD-blocked, same as age-verification"
    (let [s (store/mem-store {"store-1" store-1})
          poisoned (assoc (clean-proposal :log-sales-record "store-1")
                          :rationale "decided to override responsible service of alcohol determination for case 9"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest id-scanner-direct-control-content-is-hard
  (testing "a proposal touching direct point-of-sale ID-scanner/age-verification-terminal actuation is HARD-blocked"
    (let [s (store/mem-store {"store-1" store-1})
          poisoned (assoc (clean-proposal :schedule-staffing-operation "store-1")
                          :summary "actuate the id scanner: control the id scanner remotely")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest licensing-authority-content-is-hard
  (testing "a proposal touching liquor-control-board/license-suspension enforcement is HARD-blocked"
    (let [s (store/mem-store {"store-1" store-1})
          poisoned (assoc (clean-proposal :coordinate-supply-order "store-1")
                          :summary "contact liquor control board enforcement for regulatory clearance and license suspension review")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest structured-age-verification-flag-is-hard
  (testing "an explicit structured :finalizes-age-verification-override? true flag on :value is HARD-blocked even with clean text fields"
    (let [s (store/mem-store {"store-1" store-1})
          poisoned (assoc (clean-proposal :log-sales-record "store-1")
                          :value {:finalizes-age-verification-override? true})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest structured-responsible-service-flag-is-hard
  (testing "an explicit structured :finalizes-responsible-service-decision? true flag on :value is HARD-blocked even with clean text fields"
    (let [s (store/mem-store {"store-1" store-1})
          poisoned (assoc (clean-proposal :flag-compliance-concern "store-1")
                          :value {:finalizes-responsible-service-decision? true})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-compliance-concern-is-not-scope-excluded
  (testing "flagging observed age-verification-failure/over-service concerns as a COMPLIANCE CONCERN (not a finalized decision) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"store-1" store-1})
          concern (assoc (clean-proposal :flag-compliance-concern "store-1")
                         :value {:concern "customer at register 2 declined to present ID, suspected underage purchase attempt"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (age-verification failure/over-service) is exactly what this op exists to surface"))))

(deftest compliance-concern-always-escalates-clean
  (testing ":flag-compliance-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"store-1" store-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-compliance-concern "store-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-supply-order-always-escalates
  (testing "a :coordinate-supply-order above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"store-1" store-1})
          expensive (assoc (clean-proposal :coordinate-supply-order "store-1")
                           :value {:item "premium spirits restock" :estimated-cost 5000.0}
                           :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-supply-order-does-not-force-escalate
  (testing "a :coordinate-supply-order at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"store-1" store-1})
          cheap (assoc (clean-proposal :coordinate-supply-order "store-1")
                       :value {:item "wine bottle labels" :estimated-cost 85.0}
                       :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

;; ----------------------------------------------------------------------
;; CRITICAL guardrail regression test: multiple sibling actors in this
;; fleet have independently discovered and fixed the SAME bug class --
;; a governor scope-exclusion term phrased as a bare noun (e.g. bare
;; "age" or bare "verification") accidentally matches inside the mock
;; advisor's OWN default rationale/disclaimer text for a legitimate,
;; allowed proposal, causing the actor to self-block on its own happy
;; path. This test asserts every default mock-advisor proposal, for
;; every op in the closed allowlist, at a REGISTERED+VERIFIED store,
;; clears the governor with `:scope-excluded` absent from its
;; violations (regardless of `:hard?`/`:escalate?` -- some ops legally
;; escalate, e.g. :flag-compliance-concern, but MUST NOT self-trip the
;; scope-exclusion check to get there).
;; ----------------------------------------------------------------------
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own proposals, for every allowed op, never trigger :scope-excluded"
    (let [s (store/mem-store {"store-1" store-1})]
      (doseq [op [:log-sales-record :schedule-staffing-operation :coordinate-supply-order
                  :flag-compliance-concern]]
        (let [proposal (adv/infer nil {:op op :store-id "store-1"
                                        :patch {:units-sold 10 :item "test"
                                                :estimated-cost 85.0
                                                :concern "routine ID check"}})
              verdict (gov/check {:store-id "store-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default mock advisor's own proposal for " op
                   " must never self-trip :scope-excluded -- rationale/summary: "
                   (pr-str (select-keys proposal [:rationale :summary])))))))))
