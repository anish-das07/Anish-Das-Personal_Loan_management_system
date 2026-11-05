package com.example.plms.core.eligibility;

import com.example.plms.core.money.EmiCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic, versioned eligibility evaluation (case study sections 2 and 12).
 *
 * <p>Eligibility is an assessment, not an approval. Each rule returns PASS / FAIL / REVIEW / PENDING
 * with a reason code and a customer-safe explanation. The thresholds come from the policy version;
 * the values used in tests and seed data are examples only, not lending standards.
 *
 * <p>Outcome precedence: any FAIL -> NOT_ELIGIBLE; else any REVIEW -> MANUAL_REVIEW;
 * else any PENDING -> PENDING_PROVIDER; else ELIGIBLE.
 */
public final class EligibilityEngine {

    private final EmiCalculator emiCalculator = new EmiCalculator();

    public enum RuleOutcome { PASS, FAIL, REVIEW, PENDING }

    public enum Outcome { ELIGIBLE, NOT_ELIGIBLE, MANUAL_REVIEW, PENDING_PROVIDER }

    public enum CreditStatus { AVAILABLE, UNAVAILABLE, NOT_REQUESTED }

    public record Policy(String version,
                         BigDecimal minMonthlyIncome,
                         BigDecimal passMaxDebtToIncome,
                         BigDecimal reviewMaxDebtToIncome,
                         int minEmploymentMonths,
                         Set<String> allowedEmploymentTypes,
                         int minAge,
                         int maxAge,
                         boolean creditRequired,
                         int creditRejectBelow,
                         int creditReviewBelow) {
    }

    public record Input(BigDecimal requestedAmount,
                        int requestedMonths,
                        BigDecimal productMinAmount,
                        BigDecimal productMaxAmount,
                        int productMinMonths,
                        int productMaxMonths,
                        BigDecimal annualRatePercent,
                        BigDecimal monthlyIncome,
                        BigDecimal existingMonthlyObligations,
                        String employmentType,
                        Integer employmentMonths,
                        Integer ageYears,
                        CreditStatus creditStatus,
                        Integer creditScore) {
    }

    public record RuleResult(String code, RuleOutcome result, String reasonCode, String explanation) {
    }

    public record Result(String policyVersion, Outcome outcome, BigDecimal estimatedEmi,
                         BigDecimal debtToIncomeRatio, List<RuleResult> rules) {
    }

    public Result evaluate(Policy policy, Input in) {
        List<RuleResult> rules = new ArrayList<>();

        // 1. Product range
        boolean amountOk = in.requestedAmount() != null
                && in.requestedAmount().compareTo(in.productMinAmount()) >= 0
                && in.requestedAmount().compareTo(in.productMaxAmount()) <= 0;
        boolean monthsOk = in.requestedMonths() >= in.productMinMonths()
                && in.requestedMonths() <= in.productMaxMonths();
        rules.add(amountOk && monthsOk
                ? new RuleResult("PRODUCT_RANGE", RuleOutcome.PASS, "WITHIN_LIMITS",
                "Requested amount and tenure are within product limits")
                : new RuleResult("PRODUCT_RANGE", RuleOutcome.FAIL, "OUTSIDE_LIMITS",
                "Requested amount or tenure is outside product limits"));

        // 2. Minimum income
        BigDecimal income = nz(in.monthlyIncome());
        rules.add(income.compareTo(policy.minMonthlyIncome()) >= 0
                ? new RuleResult("MIN_INCOME", RuleOutcome.PASS, "MIN_INCOME_MET", "Configured minimum met")
                : new RuleResult("MIN_INCOME", RuleOutcome.FAIL, "MIN_INCOME_NOT_MET",
                "Monthly income is below the configured minimum"));

        // 3. Affordability (debt-to-income)
        BigDecimal emi = null;
        BigDecimal dti = null;
        if (amountOk && monthsOk && income.signum() > 0) {
            emi = emiCalculator.calculate(in.requestedAmount(), in.annualRatePercent(), in.requestedMonths());
            dti = nz(in.existingMonthlyObligations()).add(emi).divide(income, 4, RoundingMode.HALF_UP);
            if (dti.compareTo(policy.passMaxDebtToIncome()) <= 0) {
                rules.add(new RuleResult("AFFORDABILITY", RuleOutcome.PASS, "DTI_WITHIN_LIMIT",
                        "Repayment appears affordable under the configured policy"));
            } else if (dti.compareTo(policy.reviewMaxDebtToIncome()) <= 0) {
                rules.add(new RuleResult("AFFORDABILITY", RuleOutcome.REVIEW, "DTI_REVIEW_BAND",
                        "Within manual-review band"));
            } else {
                rules.add(new RuleResult("AFFORDABILITY", RuleOutcome.FAIL, "DTI_ABOVE_LIMIT",
                        "Estimated repayments are too high relative to income"));
            }
        } else {
            rules.add(new RuleResult("AFFORDABILITY", RuleOutcome.FAIL, "AFFORDABILITY_NOT_COMPUTABLE",
                    "Affordability could not be assessed with the information provided"));
        }

        // 4. Employment
        String type = in.employmentType();
        if (type == null || !policy.allowedEmploymentTypes().contains(type)) {
            rules.add(new RuleResult("EMPLOYMENT", RuleOutcome.FAIL, "EMPLOYMENT_TYPE_NOT_ELIGIBLE",
                    "Employment type is not eligible for this product"));
        } else if (in.employmentMonths() == null) {
            rules.add(new RuleResult("EMPLOYMENT", RuleOutcome.REVIEW, "EMPLOYMENT_DURATION_UNKNOWN",
                    "Employment duration needs to be confirmed by a specialist"));
        } else if (in.employmentMonths() < policy.minEmploymentMonths()) {
            rules.add(new RuleResult("EMPLOYMENT", RuleOutcome.REVIEW, "EMPLOYMENT_DURATION_SHORT",
                    "Employment duration needs a specialist review"));
        } else {
            rules.add(new RuleResult("EMPLOYMENT", RuleOutcome.PASS, "EMPLOYMENT_MET",
                    "Employment meets the configured rule"));
        }

        // 5. Age
        if (in.ageYears() == null) {
            rules.add(new RuleResult("AGE", RuleOutcome.FAIL, "AGE_UNKNOWN", "Date of birth is required"));
        } else if (in.ageYears() < policy.minAge() || in.ageYears() > policy.maxAge()) {
            rules.add(new RuleResult("AGE", RuleOutcome.FAIL, "AGE_OUTSIDE_RANGE",
                    "Applicant age is outside the configured range"));
        } else {
            rules.add(new RuleResult("AGE", RuleOutcome.PASS, "AGE_MET", "Age meets the configured range"));
        }

        // 6. Credit data (synthetic). Unavailable data is PENDING, never a failed score.
        if (policy.creditRequired()) {
            if (in.creditStatus() != CreditStatus.AVAILABLE || in.creditScore() == null) {
                rules.add(new RuleResult("CREDIT_DATA", RuleOutcome.PENDING, "PROVIDER_UNAVAILABLE",
                        "Synthetic provider timed out or was unavailable"));
            } else if (in.creditScore() < policy.creditRejectBelow()) {
                rules.add(new RuleResult("CREDIT_DATA", RuleOutcome.FAIL, "CREDIT_BAND_BELOW_POLICY",
                        "Credit assessment does not meet the configured band"));
            } else if (in.creditScore() < policy.creditReviewBelow()) {
                rules.add(new RuleResult("CREDIT_DATA", RuleOutcome.REVIEW, "CREDIT_BAND_REVIEW",
                        "Credit assessment requires a specialist review"));
            } else {
                rules.add(new RuleResult("CREDIT_DATA", RuleOutcome.PASS, "CREDIT_BAND_MET",
                        "Credit assessment meets the configured band"));
            }
        }

        return new Result(policy.version(), aggregate(rules), emi, dti, List.copyOf(rules));
    }

    public static Outcome aggregate(List<RuleResult> rules) {
        boolean review = false;
        boolean pending = false;
        for (RuleResult r : rules) {
            switch (r.result()) {
                case FAIL -> {
                    return Outcome.NOT_ELIGIBLE;
                }
                case REVIEW -> review = true;
                case PENDING -> pending = true;
                default -> { }
            }
        }
        if (review) {
            return Outcome.MANUAL_REVIEW;
        }
        return pending ? Outcome.PENDING_PROVIDER : Outcome.ELIGIBLE;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
