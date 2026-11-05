package com.example.plms.eligibility;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class EligibilityDtos {

    private EligibilityDtos() {
    }

    /** Everything the engine needs, assembled by the application module. */
    public record EvaluationCommand(String applicationId, String customerId, String policyVersion,
                                    BigDecimal requestedAmount, int requestedMonths, BigDecimal minAmount,
                                    BigDecimal maxAmount, int minMonths, int maxMonths, BigDecimal annualRate,
                                    BigDecimal monthlyIncome, BigDecimal existingMonthlyObligations,
                                    String employmentType, Integer employmentMonths, Integer ageYears) {
    }

    public record EvaluateRequest(@NotBlank String applicationId, String policyVersion) {
    }

    public record RuleView(String code, String result, String reasonCode, String reason) {
    }

    public record AssessmentResponse(String assessmentId, String applicationId, String outcome, String policyVersion,
                                     BigDecimal estimatedEmi, BigDecimal debtToIncomeRatio, List<RuleView> rules,
                                     Instant evaluatedAt) {
    }

    /** Customer-safe summary: outcome, counts and safe explanations only. */
    public record CustomerAssessmentView(String assessmentId, String outcome, String policyVersion, long passed,
                                         long needsReview, long pending, long failed, List<RuleView> rules,
                                         Instant evaluatedAt) {
    }

    public record PolicyRequest(
            @NotBlank @Size(max = 40) String policyVersion,
            @NotNull @DecimalMin("0.00") BigDecimal minMonthlyIncome,
            @NotNull @DecimalMin("0.0000") @DecimalMax("5.0000") BigDecimal passMaxDti,
            @NotNull @DecimalMin("0.0000") @DecimalMax("5.0000") BigDecimal reviewMaxDti,
            @Min(0) @Max(600) int minEmploymentMonths,
            @NotEmpty Set<String> allowedEmploymentTypes,
            @Min(18) @Max(100) int minAge,
            @Min(18) @Max(100) int maxAge,
            boolean creditRequired,
            @Min(300) @Max(900) int creditRejectBelow,
            @Min(300) @Max(900) int creditReviewBelow,
            boolean autoRejectOnFail) {
    }

    public record PolicyResponse(String policyVersion, BigDecimal minMonthlyIncome, BigDecimal passMaxDti,
                                 BigDecimal reviewMaxDti, int minEmploymentMonths, Set<String> allowedEmploymentTypes,
                                 int minAge, int maxAge, boolean creditRequired, int creditRejectBelow,
                                 int creditReviewBelow, boolean autoRejectOnFail, Instant createdAt) {
        public static PolicyResponse from(EligibilityPolicyEntity e) {
            return new PolicyResponse(e.getPolicyVersion(), e.getMinMonthlyIncome(), e.getPassMaxDti(),
                    e.getReviewMaxDti(), e.getMinEmploymentMonths(), e.employmentTypes(), e.getMinAge(), e.getMaxAge(),
                    e.isCreditRequired(), e.getCreditRejectBelow(), e.getCreditReviewBelow(), e.isAutoRejectOnFail(),
                    e.getCreatedAt());
        }
    }
}
