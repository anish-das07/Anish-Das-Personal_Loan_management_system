package com.example.plms.disbursement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class DisbursementDtos {

    private DisbursementDtos() {
    }

    /** simulatedOutcome is a training aid to demonstrate success, failure and unknown (timeout) outcomes. */
    public record CreateRequest(@NotNull @DecimalMin("1.00") @Digits(integer = 13, fraction = 2) BigDecimal grossAmount,
                                @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal feeAmount,
                                SimulatedBankAdapter.Mode simulatedOutcome) {
    }

    public record DisbursementResponse(String disbursementId, String loanAccountId, String idempotencyKey,
                                       BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount,
                                       String status, String transactionRef, String failureReason,
                                       Instant createdAt, Instant updatedAt, String note) {
        public static DisbursementResponse from(Disbursement d) {
            return new DisbursementResponse(d.getId(), d.getLoanAccountId(), d.getIdempotencyKey(),
                    d.getGrossAmount(), d.getFeeAmount(), d.getNetAmount(), d.getStatus(), d.getTransactionRef(),
                    d.getFailureReason(), d.getCreatedAt(), d.getUpdatedAt(),
                    "Simulated transfer - no real money movement");
        }
    }
}
