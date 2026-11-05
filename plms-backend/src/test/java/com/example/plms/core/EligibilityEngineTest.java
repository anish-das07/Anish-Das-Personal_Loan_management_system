package com.example.plms.core;

import com.example.plms.core.eligibility.EligibilityEngine;
import com.example.plms.core.eligibility.EligibilityEngine.CreditStatus;
import com.example.plms.core.eligibility.EligibilityEngine.Input;
import com.example.plms.core.eligibility.EligibilityEngine.Outcome;
import com.example.plms.core.eligibility.EligibilityEngine.Policy;
import com.example.plms.core.eligibility.EligibilityEngine.RuleOutcome;
import com.example.plms.core.eligibility.EligibilityEngine.RuleResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic rule evaluation, boundary values and outcome precedence. */
class EligibilityEngineTest {

    private final EligibilityEngine engine = new EligibilityEngine();
    private final Policy policy = new Policy("P-1", new BigDecimal("25000"), new BigDecimal("0.40"),
            new BigDecimal("0.55"), 6, Set.of("SALARIED", "SELF_EMPLOYED"), 21, 60, true, 600, 650);

    private Input input(String amount, int months, String income, String obligations, String type, Integer empMonths,
                        Integer age, CreditStatus credit, Integer score) {
        return new Input(new BigDecimal(amount), months, new BigDecimal("50000"), new BigDecimal("1500000"), 6, 60,
                new BigDecimal("12"), new BigDecimal(income), new BigDecimal(obligations), type, empMonths, age,
                credit, score);
    }

    private Input good() {
        return input("300000", 36, "85000", "12000", "SALARIED", 24, 30, CreditStatus.AVAILABLE, 760);
    }

    @Test
    void eligibleApplicantPassesEveryRule() {
        EligibilityEngine.Result r = engine.evaluate(policy, good());
        assertThat(r.outcome()).isEqualTo(Outcome.ELIGIBLE);
        assertThat(r.rules()).hasSize(6).allMatch(x -> x.result() == RuleOutcome.PASS);
        assertThat(r.policyVersion()).isEqualTo("P-1");
        assertThat(r.estimatedEmi()).isEqualByComparingTo("9964.29");
        assertThat(r.debtToIncomeRatio()).isEqualByComparingTo("0.2584");
    }

    @Test
    void evaluationIsDeterministic() {
        assertThat(engine.evaluate(policy, good())).isEqualTo(engine.evaluate(policy, good()));
    }

    @ParameterizedTest
    @CsvSource({"25000.00,ELIGIBLE", "24999.99,NOT_ELIGIBLE"})
    void minimumIncomeBoundary(String income, Outcome expected) {
        Input in = input("50000", 12, income, "0", "SALARIED", 24, 30, CreditStatus.AVAILABLE, 760);
        assertThat(engine.evaluate(policy, in).outcome()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"21,ELIGIBLE", "60,ELIGIBLE", "20,NOT_ELIGIBLE", "61,NOT_ELIGIBLE"})
    void ageBoundaries(int age, Outcome expected) {
        Input in = input("300000", 36, "85000", "12000", "SALARIED", 24, age, CreditStatus.AVAILABLE, 760);
        assertThat(engine.evaluate(policy, in).outcome()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"650,ELIGIBLE", "649,MANUAL_REVIEW", "600,MANUAL_REVIEW", "599,NOT_ELIGIBLE"})
    void creditBandBoundaries(int score, Outcome expected) {
        Input in = input("300000", 36, "85000", "12000", "SALARIED", 24, 30, CreditStatus.AVAILABLE, score);
        assertThat(engine.evaluate(policy, in).outcome()).isEqualTo(expected);
    }

    @Test
    void unavailableCreditDataIsPendingNotFailed() {
        Input in = input("300000", 36, "85000", "12000", "SALARIED", 24, 30, CreditStatus.UNAVAILABLE, null);
        EligibilityEngine.Result r = engine.evaluate(policy, in);
        assertThat(r.outcome()).isEqualTo(Outcome.PENDING_PROVIDER);
        assertThat(r.rules().get(5).result()).isEqualTo(RuleOutcome.PENDING);
    }

    @Test
    void affordabilityBands() {
        // (25000 + 19928.59) / 85000 = 0.5286 -> review band
        Input review = input("600000", 36, "85000", "25000", "SALARIED", 24, 30, CreditStatus.AVAILABLE, 760);
        assertThat(engine.evaluate(policy, review).outcome()).isEqualTo(Outcome.MANUAL_REVIEW);
        Input fail = input("600000", 36, "85000", "40000", "SALARIED", 24, 30, CreditStatus.AVAILABLE, 760);
        assertThat(engine.evaluate(policy, fail).outcome()).isEqualTo(Outcome.NOT_ELIGIBLE);
    }

    @Test
    void shortEmploymentGoesToReviewAndIneligibleTypeFails() {
        Input shortJob = input("300000", 36, "85000", "12000", "SALARIED", 3, 30, CreditStatus.AVAILABLE, 760);
        assertThat(engine.evaluate(policy, shortJob).outcome()).isEqualTo(Outcome.MANUAL_REVIEW);
        Input other = input("300000", 36, "85000", "12000", "OTHER", 24, 30, CreditStatus.AVAILABLE, 760);
        assertThat(engine.evaluate(policy, other).outcome()).isEqualTo(Outcome.NOT_ELIGIBLE);
    }

    @Test
    void outsideProductRangeFails() {
        Input in = input("10000", 36, "85000", "12000", "SALARIED", 24, 30, CreditStatus.AVAILABLE, 760);
        assertThat(engine.evaluate(policy, in).rules().get(0).result()).isEqualTo(RuleOutcome.FAIL);
    }

    @Test
    void outcomePrecedenceIsFailThenReviewThenPending() {
        RuleResult pass = new RuleResult("A", RuleOutcome.PASS, "x", "x");
        RuleResult review = new RuleResult("B", RuleOutcome.REVIEW, "x", "x");
        RuleResult pending = new RuleResult("C", RuleOutcome.PENDING, "x", "x");
        RuleResult fail = new RuleResult("D", RuleOutcome.FAIL, "x", "x");
        assertThat(EligibilityEngine.aggregate(List.of(pass, review, pending, fail))).isEqualTo(Outcome.NOT_ELIGIBLE);
        assertThat(EligibilityEngine.aggregate(List.of(pass, review, pending))).isEqualTo(Outcome.MANUAL_REVIEW);
        assertThat(EligibilityEngine.aggregate(List.of(pass, pending))).isEqualTo(Outcome.PENDING_PROVIDER);
        assertThat(EligibilityEngine.aggregate(List.of(pass))).isEqualTo(Outcome.ELIGIBLE);
    }
}
