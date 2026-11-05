package com.example.plms.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Metadata only; the file itself lives in private storage under a random object key. */
@Entity
@Table(name = "document_metadata",
        indexes = @Index(name = "idx_doc_app_status", columnList = "application_id, status"))
public class DocumentMetadata {

    public enum Status { PENDING_REVIEW, VERIFIED, REJECTED, RESUBMISSION_REQUIRED, SUPERSEDED }

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;
    @Column(nullable = false, length = 30)
    private String category;
    @Column(name = "object_key", nullable = false, length = 80)
    private String objectKey;
    @Column(name = "file_name_safe", nullable = false, length = 100)
    private String fileNameSafe;
    @Column(name = "mime_type", nullable = false, length = 60)
    private String mimeType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String checksum;
    @Column(name = "doc_version", nullable = false)
    private int docVersion;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
    @Version
    private Long lockVersion;

    protected DocumentMetadata() {
    }

    public DocumentMetadata(String applicationId, String customerId, String category, String objectKey,
                            String fileNameSafe, String mimeType, long sizeBytes, String checksum, int docVersion,
                            Instant now) {
        this.id = UUID.randomUUID().toString();
        this.applicationId = applicationId;
        this.customerId = customerId;
        this.category = category;
        this.objectKey = objectKey;
        this.fileNameSafe = fileNameSafe;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.docVersion = docVersion;
        this.status = Status.PENDING_REVIEW.name();
        this.uploadedAt = now;
    }

    public void setStatus(Status s) {
        this.status = s.name();
    }

    public boolean is(Status s) {
        return s.name().equals(status);
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

    public String getCategory() {
        return category;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getFileNameSafe() {
        return fileNameSafe;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public int getDocVersion() {
        return docVersion;
    }

    public String getStatus() {
        return status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
