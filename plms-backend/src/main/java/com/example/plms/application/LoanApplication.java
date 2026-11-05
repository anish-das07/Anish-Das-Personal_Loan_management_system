package com.example.plms.application;

import com.example.plms.core.workflow.ApplicationStatus;
import com.example.plms.product.LoanProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Loan application aggregate. Product terms are snapshotted at submission so later product edits
 * never silently change an existing application. {@code @Version} detects concurrent updates.
 */
@Entity
@Table(name = "loan_application", indexes = {
        @Index(name = "idx_app_customer_status", columnList = "customer_id, status"),
        @Index(name = "idx_app_submitted_at", columnList = "submitted_at")})
public class LoanApplication {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;
    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;
    @Column(name = "product_name", length = 100)
    private String productName;
    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;
    @Column(name = "requested_months", nullable = false)
    private int requestedMonths;
    @Column(nullable = false, length = 80)
    private String purpose;
    @Column(name = "employment_type", length = 30)
    private String employmentType;
    @Column(name = "employer_name", length = 120)
    private String employerName;
    @Column(name = "employment_start_date")
    private LocalDate employmentStartDate;
    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;
    @Column(name = "existing_monthly_obligations", precision = 15, scale = 2)
    private BigDecimal existingMonthlyObligations;
    @Column(name = "monthly_expenses", precision = 15, scale = 2)
    private BigDecimal monthlyExpenses;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApplicationStatus status;
    @Column(name = "policy_version", length = 40)
    private String policyVersion;
    @Lob
    @Column(name = "terms_snapshot")
    private String termsSnapshot;
    @Column(name = "snapshot_annual_rate", precision = 5, scale = 2)
    private BigDecimal snapshotAnnualRate;
    @Column(name = "snapshot_fee_percent", precision = 5, scale = 2)
    private BigDecimal snapshotFeePercent;
    @Column(name = "snapshot_min_amount", precision = 15, scale = 2)
    private BigDecimal snapshotMinAmount;
    @Column(name = "snapshot_max_amount", precision = 15, scale = 2)
    private BigDecimal snapshotMaxAmount;
    @Column(name = "snapshot_min_months")
    private Integer snapshotMinMonths;
    @Column(name = "snapshot_max_months")
    private Integer snapshotMaxMonths;
    @Column(name = "eligibility_outcome", length = 30)
    private String eligibilityOutcome;
    @Column(name = "assigned_officer_id", length = 36)
    private String assignedOfficerId;
    @Column(name = "create_idempotency_key", length = 100)
    private String createIdempotencyKey;
    @Column(name = "submit_idempotency_key", length = 100)
    private String submitIdempotencyKey;
    @Column(nullable = false)
    private int revision;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected LoanApplication() {
    }

    public static LoanApplication draft(String customerId, LoanProduct product, ApplicationDtos.CreateRequest r,
                                        String idempotencyKey, Instant now) {
        LoanApplication a = new LoanApplication();
        a.id = UUID.randomUUID().toString();
        a.customerId = customerId;
        a.productId = product.getId();
        a.productName = product.getName();
        a.requestedAmount = r.requestedAmount();
        a.requestedMonths = r.requestedMonths();
        a.purpose = r.purpose().trim();
        a.employmentType = r.employmentType();
        a.employerName = r.employerName();
        a.employmentStartDate = r.employmentStartDate();
        a.monthlyIncome = r.monthlyIncome();
        a.existingMonthlyObligations = r.existingMonthlyObligations();
        a.monthlyExpenses = r.monthlyExpenses();
        a.status = ApplicationStatus.DRAFT;
        a.createIdempotencyKey = idempotencyKey;
        a.createdAt = now;
        a.updatedAt = now;
        return a;
    }

    public void applyPatch(ApplicationDtos.UpdateRequest r, LoanProduct product, Instant now) {
        if (product != null) {
            this.productId = product.getId();
            this.productName = product.getName();
        }
        if (r.requestedAmount() != null) {
            this.requestedAmount = r.requestedAmount();
        }
        if (r.requestedMonths() != null) {
            this.requestedMonths = r.requestedMonths();
        }
        if (r.purpose() != null) {
            this.purpose = r.purpose().trim();
        }
        if (r.employmentType() != null) {
            this.employmentType = r.employmentType();
        }
        if (r.employerName() != null) {
            this.employerName = r.employerName();
        }
        if (r.employmentStartDate() != null) {
            this.employmentStartDate = r.employmentStartDate();
        }
        if (r.monthlyIncome() != null) {
            this.monthlyIncome = r.monthlyIncome();
        }
        if (r.existingMonthlyObligations() != null) {
            this.existingMonthlyObligations = r.existingMonthlyObligations();
        }
        if (r.monthlyExpenses() != null) {
            this.monthlyExpenses = r.monthlyExpenses();
        }
        this.updatedAt = now;
    }

    public void snapshotTerms(LoanProduct p, Instant now) {
        this.productName = p.getName();
        this.policyVersion = p.getPolicyVersion();
        this.snapshotAnnualRate = p.getAnnualRate();
        this.snapshotFeePercent = p.getProcessingFeePercent();
        this.snapshotMinAmount = p.getMinAmount();
        this.snapshotMaxAmount = p.getMaxAmount();
        this.snapshotMinMonths = p.getMinMonths();
        this.snapshotMaxMonths = p.getMaxMonths();
        this.termsSnapshot = "{\"productId\":\"" + p.getId() + "\",\"code\":\"" + p.getCode()
                + "\",\"name\":\"" + p.getName().replace("\"", "'") + "\",\"annualRate\":" + p.getAnnualRate()
                + ",\"interestMethod\":\"" + p.getInterestMethod() + "\",\"processingFeePercent\":"
                + p.getProcessingFeePercent() + ",\"minAmount\":" + p.getMinAmount() + ",\"maxAmount\":"
                + p.getMaxAmount() + ",\"minMonths\":" + p.getMinMonths() + ",\"maxMonths\":" + p.getMaxMonths()
                + ",\"policyVersion\":\"" + p.getPolicyVersion() + "\",\"snapshotAt\":\"" + now + "\"}";
    }

    public ApplicationStatus transitionTo(ApplicationStatus next, Instant now) {
        ApplicationStatus previous = this.status;
        previous.assertTransition(next);
        this.status = next;
        this.updatedAt = now;
        if (next == ApplicationStatus.SUBMITTED) {
            this.submittedAt = now;
            this.revision++;
        }
        return previous;
    }

    public void recordEligibility(String outcome, Instant now) {
        this.eligibilityOutcome = outcome;
        this.updatedAt = now;
    }

    public void assignTo(String officerId, Instant now) {
        this.assignedOfficerId = officerId;
        this.updatedAt = now;
    }

    public void markSubmitKey(String key) {
        this.submitIdempotencyKey = key;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public int getRequestedMonths() {
        return requestedMonths;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public String getEmployerName() {
        return employerName;
    }

    public LocalDate getEmploymentStartDate() {
        return employmentStartDate;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getExistingMonthlyObligations() {
        return existingMonthlyObligations;
    }

    public BigDecimal getMonthlyExpenses() {
        return monthlyExpenses;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public String getTermsSnapshot() {
        return termsSnapshot;
    }

    public BigDecimal getSnapshotAnnualRate() {
        return snapshotAnnualRate;
    }

    public BigDecimal getSnapshotFeePercent() {
        return snapshotFeePercent;
    }

    public BigDecimal getSnapshotMinAmount() {
        return snapshotMinAmount;
    }

    public BigDecimal getSnapshotMaxAmount() {
        return snapshotMaxAmount;
    }

    public Integer getSnapshotMinMonths() {
        return snapshotMinMonths;
    }

    public Integer getSnapshotMaxMonths() {
        return snapshotMaxMonths;
    }

    public String getEligibilityOutcome() {
        return eligibilityOutcome;
    }

    public String getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public String getCreateIdempotencyKey() {
        return createIdempotencyKey;
    }

    public String getSubmitIdempotencyKey() {
        return submitIdempotencyKey;
    }

    public int getRevision() {
        return revision;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
