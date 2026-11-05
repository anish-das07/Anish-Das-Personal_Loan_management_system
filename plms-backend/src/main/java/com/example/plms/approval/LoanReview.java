package com.example.plms.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A maker's recommendation (loan officer or credit analyst). */
@Entity
@Table(name = "loan_review", indexes = @Index(name = "idx_review_app", columnList = "application_id"))
public class LoanReview {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;
    @Column(name = "reviewer_id", nullable = false, length = 36)
    private String reviewerId;
    @Column(name = "reviewer_role", nullable = false, length = 30)
    private String reviewerRole;
    @Column(nullable = false, length = 30)
    private String recommendation;
    @Column(name = "reason_code", nullable = false, length = 60)
    private String reasonCode;
    @Column(name = "comment_text", length = 500)
    private String comment;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoanReview() {
    }

    public LoanReview(String applicationId, String reviewerId, String reviewerRole, String recommendation,
                      String reasonCode, String comment, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.applicationId = applicationId;
        this.reviewerId = reviewerId;
        this.reviewerRole = reviewerRole;
        this.recommendation = recommendation;
        this.reasonCode = reasonCode;
        this.comment = comment;
        this.createdAt = now;
    }

    public String getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public String getReviewerRole() {
        return reviewerRole;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
