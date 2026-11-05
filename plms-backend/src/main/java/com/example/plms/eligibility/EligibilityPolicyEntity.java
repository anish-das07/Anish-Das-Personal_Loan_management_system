package com.example.plms.eligibility;

import com.example.plms.core.eligibility.EligibilityEngine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** A versioned, immutable eligibility policy. A change means a new version, not an edit. */
@Entity
@Table(name = "eligibility_policy")
public class EligibilityPolicyEntity {

    @Id
    @Column(name = "policy_version", length = 40)
    private String policyVersion;
    @Column(name = "min_monthly_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal minMonthlyIncome;
    @Column(name = "pass_max_dti", nullable = false, precision = 5, scale = 4)
    private BigDecimal passMaxDti;
    @Column(name = "review_max_dti", nullable = false, precision = 5, scale = 4)
    private BigDecimal reviewMaxDti;
    @Column(name = "min_employment_months", nullable = false)
    private int minEmploymentMonths;
    @Column(name = "allowed_employment_types", nullable = false, length = 120)
    private String allowedEmploymentTypes;
    @Column(name = "min_age", nullable = false)
    private int minAge;
    @Column(name = "max_age", nullable = false)
    private int maxAge;
    @Column(name = "credit_required", nullable = false)
    private boolean creditRequired;
    @Column(name = "credit_reject_below", nullable = false)
    private int creditRejectBelow;
    @Column(name = "credit_review_below", nullable = false)
    private int creditReviewBelow;
    @Column(name = "auto_reject_on_fail", nullable = false)
    private boolean autoRejectOnFail;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EligibilityPolicyEntity() {
    }

    public EligibilityPolicyEntity(EligibilityDtos.PolicyRequest r, Instant now) {
        if (r.passMaxDti().compareTo(r.reviewMaxDti()) > 0 || r.minAge() > r.maxAge()
                || r.creditRejectBelow() > r.creditReviewBelow()) {
            throw new IllegalArgumentException("Policy thresholds are inconsistent.");
        }
        this.policyVersion = r.policyVersion();
        this.minMonthlyIncome = r.minMonthlyIncome();
        this.passMaxDti = r.passMaxDti();
        this.reviewMaxDti = r.reviewMaxDti();
        this.minEmploymentMonths = r.minEmploymentMonths();
        this.allowedEmploymentTypes = String.join(",", r.allowedEmploymentTypes());
        this.minAge = r.minAge();
        this.maxAge = r.maxAge();
        this.creditRequired = r.creditRequired();
        this.creditRejectBelow = r.creditRejectBelow();
        this.creditReviewBelow = r.creditReviewBelow();
        this.autoRejectOnFail = r.autoRejectOnFail();
        this.createdAt = now;
    }

    public EligibilityEngine.Policy toPolicy() {
        return new EligibilityEngine.Policy(policyVersion, minMonthlyIncome, passMaxDti, reviewMaxDti,
                minEmploymentMonths, employmentTypes(), minAge, maxAge, creditRequired, creditRejectBelow,
                creditReviewBelow);
    }

    public Set<String> employmentTypes() {
        return Arrays.stream(allowedEmploymentTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public BigDecimal getMinMonthlyIncome() {
        return minMonthlyIncome;
    }

    public BigDecimal getPassMaxDti() {
        return passMaxDti;
    }

    public BigDecimal getReviewMaxDti() {
        return reviewMaxDti;
    }

    public int getMinEmploymentMonths() {
        return minEmploymentMonths;
    }

    public int getMinAge() {
        return minAge;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public boolean isCreditRequired() {
        return creditRequired;
    }

    public int getCreditRejectBelow() {
        return creditRejectBelow;
    }

    public int getCreditReviewBelow() {
        return creditReviewBelow;
    }

    public boolean isAutoRejectOnFail() {
        return autoRejectOnFail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
