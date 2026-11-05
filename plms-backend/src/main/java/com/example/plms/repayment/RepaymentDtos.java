package com.example.plms.repayment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public final class RepaymentDtos {

    private RepaymentDtos() {
    }

    public record CreateRequest(@NotBlank String loanAccountId,
                                @NotNull @Min(1) Integer installmentNo,
                                @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
                                @NotBlank @Pattern(regexp = "^(SIMULATED|SIMULATED_DECLINE|SIMULATED_TIMEOUT)$",
                                        message = "Only simulated payment methods are supported")
                                String paymentMethod) {
    }

    public record RepaymentResponse(String repaymentId, String loanAccountId, int installmentNo, BigDecimal amount,
                                    String paymentMethod, String status, String reference, String failureReason,
                                    String receiptUrl, Instant createdAt) {
        public static RepaymentResponse from(Repayment r) {
            return new RepaymentResponse(r.getId(), r.getLoanAccountId(), r.getInstallmentNo(), r.getAmount(),
                    r.getPaymentMethod(), r.getStatus(), r.getReference(), r.getFailureReason(),
                    r.is(Repayment.Status.SUCCESS) ? "/api/v1/repayments/" + r.getId() + "/receipt" : null,
                    r.getCreatedAt());
        }
    }

    public record Receipt(String receiptNo, String repaymentId, String loanAccountId, int installmentNo,
                          BigDecimal amount, BigDecimal feeAllocated, BigDecimal interestAllocated,
                          BigDecimal principalAllocated, String reference, String status, Instant paidAt,
                          String note) {
    }
}
