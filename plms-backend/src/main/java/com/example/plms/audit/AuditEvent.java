package com.example.plms.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit record. No setters: rows are inserted, never updated. */
@Entity
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_id"),
        @Index(name = "idx_audit_actor", columnList = "actor_id")})
public class AuditEvent {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "actor_id", length = 36)
    private String actorId;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;
    @Column(name = "entity_id", length = 100)
    private String entityId;
    @Column(name = "correlation_id", length = 80)
    private String correlationId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(nullable = false, length = 20)
    private String outcome;
    @Column(name = "metadata_redacted", length = 2000)
    private String metadataRedacted;

    protected AuditEvent() {
    }

    public AuditEvent(String actorId, String action, String entityType, String entityId, String correlationId,
                      String outcome, String metadataRedacted, Instant occurredAt) {
        this.id = UUID.randomUUID().toString();
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.outcome = outcome;
        this.metadataRedacted = metadataRedacted;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public String getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getMetadataRedacted() {
        return metadataRedacted;
    }
}
