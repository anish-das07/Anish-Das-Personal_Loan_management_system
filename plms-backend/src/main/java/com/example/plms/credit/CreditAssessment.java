package com.example.plms.credit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Synthetic credit assessment record. The source is always a simulated provider and is labelled
 * as such. When the provider fails, no score is stored (never fabricated).
 */
@Entity
@Table(name = "credit_assessment", indexes = @Index(name = "idx_credit_app", columnList = "application_id"))
public class CreditAssessment {

    public enum Status { AVAILABLE, PENDING_PROVIDER }

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;
    @Column(name = "provider_code", nullable = false, length = 40)
    private String providerCode;
    @Column(name = "provider_reference", length = 60)
    private String providerReference;
    private Integer score;
    @Column(name = "score_band", length = 20)
    private String scoreBand;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "failure_reason", length = 40)
    private String failureReason;
    @Column(name = "requested_by", length = 36)
    private String requestedBy;
    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    protected CreditAssessment() {
    }

    public static CreditAssessment available(String applicationId, String customerId, String reference, int score,
                                             String band, String requestedBy, Instant now) {
        CreditAssessment c = base(applicationId, customerId, requestedBy, now);
        c.providerReference = reference;
        c.score = score;
        c.scoreBand = band;
        c.status = Status.AVAILABLE.name();
        return c;
    }

    public static CreditAssessment pending(String applicationId, String customerId, String failureReason,
                                           String requestedBy, Instant now) {
        CreditAssessment c = base(applicationId, customerId, requestedBy, now);
        c.status = Status.PENDING_PROVIDER.name();
        c.failureReason = failureReason;
        return c;
    }

    private static CreditAssessment base(String applicationId, String customerId, String requestedBy, Instant now) {
        CreditAssessment c = new CreditAssessment();
        c.id = UUID.randomUUID().toString();
        c.applicationId = applicationId;
        c.customerId = customerId;
        c.providerCode = "SYNTHETIC-BUREAU";
        c.requestedBy = requestedBy;
        c.assessedAt = now;
        return c;
    }

    public boolean isAvailable() {
        return Status.AVAILABLE.name().equals(status);
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

    public String getProviderCode() {
        return providerCode;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Integer getScore() {
        return score;
    }

    public String getScoreBand() {
        return scoreBand;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }
}
