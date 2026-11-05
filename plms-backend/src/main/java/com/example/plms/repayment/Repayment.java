package com.example.plms.repayment;

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

@Entity
@Table(name = "repayment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_repayment_idempotency", columnNames = "idempotency_key"),
                @UniqueConstraint(name = "uq_repayment_reference", columnNames = "reference")},
        indexes = @Index(name = "idx_repayment_account", columnList = "loan_account_id"))
public class Repayment {

    public enum Status { PENDING, SUCCESS, FAILED }

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "loan_account_id", nullable = false, length = 36)
    private String loanAccountId;
    @Column(name = "installment_id", nullable = false, length = 36)
    private String installmentId;
    @Column(name = "installment_no", nullable = false)
    private int installmentNo;
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 60)
    private String reference;
    @Column(name = "failure_reason", length = 60)
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected Repayment() {
    }

    public Repayment(String loanAccountId, String installmentId, int installmentNo, String customerId,
                     String idempotencyKey, BigDecimal amount, String paymentMethod, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.loanAccountId = loanAccountId;
        this.installmentId = installmentId;
        this.installmentNo = installmentNo;
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = Status.PENDING.name();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void succeed(String ref, Instant now) {
        this.status = Status.SUCCESS.name();
        this.reference = ref;
        this.updatedAt = now;
    }

    public void fail(String ref, String reason, Instant now) {
        this.status = Status.FAILED.name();
        this.reference = ref;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public boolean is(Status s) {
        return s.name().equals(status);
    }

    public boolean sameRequest(String accountId, int no, BigDecimal amt) {
        return loanAccountId.equals(accountId) && installmentNo == no && amount.compareTo(amt) == 0;
    }

    public String getId() {
        return id;
    }

    public String getLoanAccountId() {
        return loanAccountId;
    }

    public String getInstallmentId() {
        return installmentId;
    }

    public int getInstallmentNo() {
        return installmentNo;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
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
