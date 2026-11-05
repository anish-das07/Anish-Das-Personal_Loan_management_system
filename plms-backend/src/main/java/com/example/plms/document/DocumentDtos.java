package com.example.plms.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentResponse(String documentId, String applicationId, String category, String fileName,
                                   String mimeType, long sizeBytes, String status, int version, Instant uploadedAt) {
        public static DocumentResponse from(DocumentMetadata d) {
            return new DocumentResponse(d.getId(), d.getApplicationId(), d.getCategory(), d.getFileNameSafe(),
                    d.getMimeType(), d.getSizeBytes(), d.getStatus(), d.getDocVersion(), d.getUploadedAt());
        }
    }

    public record DecisionRequest(@NotBlank String decision, @Size(max = 60) String reasonCode,
                                  @Size(max = 500) String comment) {
    }

    public record VerificationView(String decision, String reasonCode, String comment, String verifierId,
                                   Instant decidedAt) {
        public static VerificationView from(DocumentVerification v) {
            return new VerificationView(v.getDecision(), v.getReasonCode(), v.getComment(), v.getVerifierId(),
                    v.getDecidedAt());
        }
    }
}
