package com.example.plms.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_product", uniqueConstraints = @UniqueConstraint(name = "uq_product_code", columnNames = "code"))
public class LoanProduct {

    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(length = 60)
    private String purpose;
    @Column(name = "min_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minAmount;
    @Column(name = "max_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmount;
    @Column(name = "min_months", nullable = false)
    private int minMonths;
    @Column(name = "max_months", nullable = false)
    private int maxMonths;
    @Column(name = "annual_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualRate;
    @Column(name = "interest_method", nullable = false, length = 30)
    private String interestMethod;
    @Column(name = "processing_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal processingFeePercent;
    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;
    @Column(name = "active_to")
    private LocalDate activeTo;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;
    @Version
    private Long version;

    protected LoanProduct() {
    }

    public LoanProduct(String code) {
        this.id = UUID.randomUUID().toString();
        this.code = code;
        this.interestMethod = "REDUCING_BALANCE";
    }

    public void apply(ProductDtos.ProductRequest r) {
        if (r.minAmount().compareTo(r.maxAmount()) > 0 || r.minMonths() > r.maxMonths()) {
            throw new IllegalArgumentException("Minimum limits must not exceed maximum limits.");
        }
        if (r.activeTo() != null && r.activeTo().isBefore(r.activeFrom())) {
            throw new IllegalArgumentException("Effective end date must not be before the start date.");
        }
        this.name = r.name();
        this.description = r.description();
        this.purpose = r.purpose();
        this.minAmount = r.minAmount();
        this.maxAmount = r.maxAmount();
        this.minMonths = r.minMonths();
        this.maxMonths = r.maxMonths();
        this.annualRate = r.annualRate();
        this.processingFeePercent = r.processingFeePercent();
        this.activeFrom = r.activeFrom();
        this.activeTo = r.activeTo();
        this.active = r.active();
        this.policyVersion = r.policyVersion();
    }

    public boolean isAvailableOn(LocalDate day) {
        return active && !day.isBefore(activeFrom) && (activeTo == null || !day.isAfter(activeTo));
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPurpose() {
        return purpose;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public int getMinMonths() {
        return minMonths;
    }

    public int getMaxMonths() {
        return maxMonths;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public String getInterestMethod() {
        return interestMethod;
    }

    public BigDecimal getProcessingFeePercent() {
        return processingFeePercent;
    }

    public LocalDate getActiveFrom() {
        return activeFrom;
    }

    public LocalDate getActiveTo() {
        return activeTo;
    }

    public boolean isActive() {
        return active;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }
}
