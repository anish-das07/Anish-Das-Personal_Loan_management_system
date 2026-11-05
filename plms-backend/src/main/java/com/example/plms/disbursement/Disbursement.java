package com.example.plms.disbursement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Simulated disbursement. The unique idempotency key and transaction reference prevent duplicates. */
@Entity
@Table(name = "disbursement",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_disbursement_idempotency", columnNames = "idempotency_key"),
                @UniqueConstraint(name = "uq_disbursement_transaction", columnNames = "transaction_ref")},
        indexes = @Index(name = "idx_disb_account", columnList = "loan_account_id"))
public class Disbursement {

    public enum Status { PROCESSING, SUCCEEDED, FAILED, UNKNOWN }

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "loan_account_id", nullable = false, length = 36)
    private String loanAccountId;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossAmount;
    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal feeAmount;
    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;
    @Column(name = "failure_reason", length = 60)
    private String failureReason;
    @Column(name = "requested_by", nullable = false, length = 36)
    private String requestedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected Disbursement() {
    }

    public Disbursement(String loanAccountId, String idempotencyKey, BigDecimal grossAmount, BigDecimal feeAmount,
                        String requestedBy, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.loanAccountId = loanAccountId;
        this.idempotencyKey = idempotencyKey;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.netAmount = grossAmount.subtract(feeAmount);
        this.status = Status.PROCESSING.name();
        this.requestedBy = requestedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void succeed(String reference, Instant now) {
        this.status = Status.SUCCEEDED.name();
        this.transactionRef = reference;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void fail(String reason, Instant now) {
        this.status = Status.FAILED.name();
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void unknown(Instant now) {
        this.status = Status.UNKNOWN.name();
        this.updatedAt = now;
    }

    public boolean is(Status s) {
        return s.name().equals(status);
    }

    public boolean sameRequest(String accountId, BigDecimal gross, BigDecimal fee) {
        return loanAccountId.equals(accountId) && grossAmount.compareTo(gross) == 0 && feeAmount.compareTo(fee) == 0;
    }

    public String getId() {
        return id;
    }

    public String getLoanAccountId() {
        return loanAccountId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
