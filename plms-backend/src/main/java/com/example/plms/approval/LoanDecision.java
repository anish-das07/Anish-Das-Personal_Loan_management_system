package com.example.plms.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable decision history. A later decision is a new row, never an overwrite. */
@Entity
@Table(name = "loan_decision", indexes = @Index(name = "idx_decision_app", columnList = "application_id"))
public class LoanDecision {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;
    @Column(nullable = false, length = 30)
    private String decision;
    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;
    @Column(name = "approved_months")
    private Integer approvedMonths;
    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;
    @Column(name = "reason_code", nullable = false, length = 60)
    private String reasonCode;
    @Column(name = "comment_redacted", length = 500)
    private String commentRedacted;
    @Column(name = "policy_version", length = 40)
    private String policyVersion;
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected LoanDecision() {
    }

    public LoanDecision(String applicationId, String decision, BigDecimal approvedAmount, Integer approvedMonths,
                        String actorId, String reasonCode, String commentRedacted, String policyVersion,
                        Instant now) {
        this.id = UUID.randomUUID().toString();
        this.applicationId = applicationId;
        this.decision = decision;
        this.approvedAmount = approvedAmount;
        this.approvedMonths = approvedMonths;
        this.actorId = actorId;
        this.reasonCode = reasonCode;
        this.commentRedacted = commentRedacted;
        this.policyVersion = policyVersion;
        this.decidedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getDecision() {
        return decision;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public Integer getApprovedMonths() {
        return approvedMonths;
    }

    public String getActorId() {
        return actorId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getCommentRedacted() {
        return commentRedacted;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
