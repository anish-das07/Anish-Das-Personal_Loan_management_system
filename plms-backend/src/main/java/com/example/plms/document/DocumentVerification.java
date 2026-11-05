package com.example.plms.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Immutable verifier decision with reason code and actor. */
@Entity
@Table(name = "document_verification", indexes = @Index(name = "idx_verif_doc", columnList = "document_id"))
public class DocumentVerification {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;
    @Column(nullable = false, length = 30)
    private String decision;
    @Column(name = "reason_code", length = 60)
    private String reasonCode;
    @Column(name = "comment_text", length = 500)
    private String comment;
    @Column(name = "verifier_id", nullable = false, length = 36)
    private String verifierId;
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected DocumentVerification() {
    }

    public DocumentVerification(String documentId, String decision, String reasonCode, String comment,
                                String verifierId, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.documentId = documentId;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.comment = comment;
        this.verifierId = verifierId;
        this.decidedAt = now;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getDecision() {
        return decision;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getComment() {
        return comment;
    }

    public String getVerifierId() {
        return verifierId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
