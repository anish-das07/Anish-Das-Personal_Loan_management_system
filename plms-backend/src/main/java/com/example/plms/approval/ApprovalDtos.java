package com.example.plms.approval;

import com.example.plms.application.ApplicationDtos;
import com.example.plms.customer.CustomerDtos;
import com.example.plms.document.DocumentService;
import com.example.plms.eligibility.EligibilityDtos;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    public record RecommendationRequest(
            @NotBlank @Pattern(regexp = "^(APPROVE|REJECT|RETURN_FOR_CORRECTION)$") String recommendation,
            @NotBlank @Size(max = 60) String reasonCode,
            @Size(max = 500) String comment) {
    }

    public record DecisionRequest(
            @NotBlank @Pattern(regexp = "^(APPROVE|REJECT|RETURN_FOR_CORRECTION)$") String decision,
            @DecimalMin("1.00") @Digits(integer = 13, fraction = 2) BigDecimal approvedAmount,
            @Min(1) @Max(120) Integer approvedMonths,
            @NotBlank @Size(max = 60) String reasonCode,
            @Size(max = 500) String comment,
            Long expectedVersion) {
    }

    public record ReviewView(String reviewId, String reviewerId, String reviewerRole, String recommendation,
                             String reasonCode, String comment, Instant createdAt) {
        static ReviewView from(LoanReview r) {
            return new ReviewView(r.getId(), r.getReviewerId(), r.getReviewerRole(), r.getRecommendation(),
                    r.getReasonCode(), r.getComment(), r.getCreatedAt());
        }
    }

    public record DecisionView(String decisionId, String applicationId, String decision, BigDecimal approvedAmount,
                               Integer approvedMonths, String actorId, String reasonCode, String comment,
                               String policyVersion, Instant decidedAt, String applicationStatus,
                               String loanAccountId) {
        static DecisionView from(LoanDecision d, String status, String loanAccountId) {
            return new DecisionView(d.getId(), d.getApplicationId(), d.getDecision(), d.getApprovedAmount(),
                    d.getApprovedMonths(), d.getActorId(), d.getReasonCode(), d.getCommentRedacted(),
                    d.getPolicyVersion(), d.getDecidedAt(), status, loanAccountId);
        }
    }

    /** Evidence pack for the approval workspace (staff only). */
    public record ReviewSummary(ApplicationDtos.ApplicationResponse application,
                                CustomerDtos.StaffProfileView applicant,
                                EligibilityDtos.AssessmentResponse eligibility,
                                Object credit,
                                List<DocumentService.ChecklistItem> documents,
                                List<String> missingMandatoryDocuments,
                                List<ReviewView> recommendations,
                                List<DecisionView> decisions) {
    }
}
