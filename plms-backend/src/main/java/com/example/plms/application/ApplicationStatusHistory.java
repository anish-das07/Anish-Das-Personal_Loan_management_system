package com.example.plms.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Immutable record of each state transition; prior states are never overwritten. */
@Entity
@Table(name = "application_status_history",
        indexes = @Index(name = "idx_history_app", columnList = "application_id, occurred_at"))
public class ApplicationStatusHistory {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;
    @Column(name = "from_status", length = 40)
    private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 40)
    private String toStatus;
    @Column(name = "actor_id", length = 36)
    private String actorId;
    @Column(name = "reason_code", length = 60)
    private String reasonCode;
    @Column(name = "customer_message", length = 300)
    private String customerMessage;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ApplicationStatusHistory() {
    }

    public ApplicationStatusHistory(String applicationId, String fromStatus, String toStatus, String actorId,
                                    String reasonCode, String customerMessage, Instant occurredAt) {
        this.id = UUID.randomUUID().toString();
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.reasonCode = reasonCode;
        this.customerMessage = customerMessage;
        this.occurredAt = occurredAt;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getActorId() {
        return actorId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getCustomerMessage() {
        return customerMessage;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
