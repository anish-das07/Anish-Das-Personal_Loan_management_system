package com.example.plms.account;

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
@Table(name = "loan_account",
        uniqueConstraints = @UniqueConstraint(name = "uq_account_application", columnNames = "application_id"),
        indexes = @Index(name = "idx_account_customer", columnList = "customer_id"))
public class LoanAccount {

    public enum Status { PENDING_DISBURSEMENT, ACTIVE, CLOSED }

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;
    @Column(name = "product_name", length = 100)
    private String productName;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principal;
    @Column(name = "annual_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualRate;
    @Column(name = "tenure_months", nullable = false)
    private int tenureMonths;
    @Column(name = "processing_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal processingFeePercent;
    @Column(name = "monthly_emi", precision = 15, scale = 2)
    private BigDecimal monthlyEmi;
    @Column(name = "outstanding_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingPrincipal;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(name = "disbursed_at")
    private Instant disbursedAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    private Long version;

    protected LoanAccount() {
    }

    public LoanAccount(String applicationId, String customerId, String productName, BigDecimal principal,
                       BigDecimal annualRate, int tenureMonths, BigDecimal processingFeePercent, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.applicationId = applicationId;
        this.customerId = customerId;
        this.productName = productName;
        this.principal = principal;
        this.annualRate = annualRate;
        this.tenureMonths = tenureMonths;
        this.processingFeePercent = processingFeePercent;
        this.outstandingPrincipal = principal;
        this.status = Status.PENDING_DISBURSEMENT.name();
        this.createdAt = now;
    }

    public void activate(BigDecimal emi, Instant now) {
        if (!is(Status.PENDING_DISBURSEMENT)) {
            throw new IllegalStateException("Account is not awaiting disbursement");
        }
        this.status = Status.ACTIVE.name();
        this.monthlyEmi = emi;
        this.disbursedAt = now;
    }

    public void reducePrincipal(BigDecimal amount) {
        this.outstandingPrincipal = this.outstandingPrincipal.subtract(amount);
    }

    public void close(Instant now) {
        this.status = Status.CLOSED.name();
        this.closedAt = now;
    }

    public boolean is(Status s) {
        return s.name().equals(status);
    }

    public String getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public BigDecimal getProcessingFeePercent() {
        return processingFeePercent;
    }

    public BigDecimal getMonthlyEmi() {
        return monthlyEmi;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public String getStatus() {
        return status;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
