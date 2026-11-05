package com.example.plms.application;

import com.example.plms.core.workflow.ApplicationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ApplicationDtos {

    public static final String EMPLOYMENT_REGEX = "^(SALARIED|SELF_EMPLOYED|OTHER)$";

    private ApplicationDtos() {
    }

    /** Create a draft. The customer id always comes from the token, never from this payload. */
    public record CreateRequest(
            @NotBlank String productId,
            @NotNull @DecimalMin("1.00") @Digits(integer = 13, fraction = 2) BigDecimal requestedAmount,
            @NotNull @Min(1) @Max(120) Integer requestedMonths,
            @NotBlank @Size(max = 80) String purpose,
            @Pattern(regexp = EMPLOYMENT_REGEX, message = "Unsupported employment type") String employmentType,
            @Size(max = 120) String employerName,
            @PastOrPresent LocalDate employmentStartDate,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal monthlyIncome,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal existingMonthlyObligations,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal monthlyExpenses) {
    }

    /** Partial update of a draft (all fields optional). */
    public record UpdateRequest(
            String productId,
            @DecimalMin("1.00") @Digits(integer = 13, fraction = 2) BigDecimal requestedAmount,
            @Min(1) @Max(120) Integer requestedMonths,
            @Size(min = 1, max = 80) String purpose,
            @Pattern(regexp = EMPLOYMENT_REGEX, message = "Unsupported employment type") String employmentType,
            @Size(max = 120) String employerName,
            @PastOrPresent LocalDate employmentStartDate,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal monthlyIncome,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal existingMonthlyObligations,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal monthlyExpenses) {
    }

    public record SubmitRequest(Boolean declarationAccepted) {
    }

    public record CreatedResponse(String applicationId, ApplicationStatus status, String message, String nextAction) {
    }

    /** Customer-safe view: no internal notes, credit scores or reviewer identities. */
    public record ApplicationResponse(String applicationId, String reference, String productId, String productName,
                                      BigDecimal requestedAmount, int requestedMonths, String purpose,
                                      String employmentType, String employerName, LocalDate employmentStartDate,
                                      BigDecimal monthlyIncome, BigDecimal existingMonthlyObligations,
                                      BigDecimal monthlyExpenses, ApplicationStatus status, String statusLabel,
                                      String eligibilityOutcome, String policyVersion, Instant submittedAt,
                                      Instant createdAt, Instant updatedAt, Long version, String nextAction) {
        public static ApplicationResponse from(LoanApplication a) {
            return new ApplicationResponse(a.getId(), ApplicationDtos.reference(a.getId()), a.getProductId(), a.getProductName(),
                    a.getRequestedAmount(), a.getRequestedMonths(), a.getPurpose(), a.getEmploymentType(),
                    a.getEmployerName(), a.getEmploymentStartDate(), a.getMonthlyIncome(),
                    a.getExistingMonthlyObligations(), a.getMonthlyExpenses(), a.getStatus(),
                    StatusLabels.label(a.getStatus()), a.getEligibilityOutcome(), a.getPolicyVersion(),
                    a.getSubmittedAt(), a.getCreatedAt(), a.getUpdatedAt(), a.getVersion(),
                    StatusLabels.nextAction(a.getStatus()));
        }
    }

    /** Staff queue row: limited PII. */
    public record QueueRow(String applicationId, String reference, String customerRef, String productName,
                           BigDecimal requestedAmount, int requestedMonths, ApplicationStatus status,
                           String eligibilityOutcome, String assignedOfficerId, Instant submittedAt, Long version) {
        public static QueueRow from(LoanApplication a) {
            return new QueueRow(a.getId(), ApplicationDtos.reference(a.getId()), "C-" + a.getCustomerId().substring(0, 8),
                    a.getProductName(), a.getRequestedAmount(), a.getRequestedMonths(), a.getStatus(),
                    a.getEligibilityOutcome(), a.getAssignedOfficerId(), a.getSubmittedAt(), a.getVersion());
        }
    }

    public record HistoryEntry(String fromStatus, String toStatus, String explanation, Instant occurredAt) {
        public static HistoryEntry from(ApplicationStatusHistory h) {
            return new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getCustomerMessage(), h.getOccurredAt());
        }
    }

    public static String reference(String id) {
        return "APP-" + id.substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}
