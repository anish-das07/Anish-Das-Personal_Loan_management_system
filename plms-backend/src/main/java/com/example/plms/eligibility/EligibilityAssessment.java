package com.example.plms.eligibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eligibility_assessment",
        indexes = @Index(name = "idx_elig_app", columnList = "application_id, evaluated_at"))
public class EligibilityAssessment {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;
    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;
    @Column(nullable = false, length = 30)
    private String outcome;
    @Column(name = "estimated_emi", precision = 15, scale = 2)
    private BigDecimal estimatedEmi;
    @Column(name = "dti_ratio", precision = 7, scale = 4)
    private BigDecimal dtiRatio;
    @Column(name = "credit_assessment_id", length = 36)
    private String creditAssessmentId;
    @Column(name = "actor_id", length = 36)
    private String actorId;
    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected EligibilityAssessment() {
    }

    public EligibilityAssessment(String applicationId, String policyVersion, String outcome, BigDecimal estimatedEmi,
                                 BigDecimal dtiRatio, String creditAssessmentId, String actorId, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.applicationId = applicationId;
        this.policyVersion = policyVersion;
        this.outcome = outcome;
        this.estimatedEmi = estimatedEmi;
        this.dtiRatio = dtiRatio;
        this.creditAssessmentId = creditAssessmentId;
        this.actorId = actorId;
        this.evaluatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public String getOutcome() {
        return outcome;
    }

    public BigDecimal getEstimatedEmi() {
        return estimatedEmi;
    }

    public BigDecimal getDtiRatio() {
        return dtiRatio;
    }

    public String getCreditAssessmentId() {
        return creditAssessmentId;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
