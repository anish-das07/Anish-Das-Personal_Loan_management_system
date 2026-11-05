package com.example.plms.document;

import com.example.plms.application.ApplicationDtos;
import com.example.plms.application.ApplicationService;
import com.example.plms.application.LoanApplication;
import com.example.plms.audit.AuditService;
import com.example.plms.auth.Role;
import com.example.plms.common.Actor;
import com.example.plms.common.ApiException;
import com.example.plms.common.PageResponse;
import com.example.plms.common.PlmsProperties;
import com.example.plms.core.document.FileInspector;
import com.example.plms.core.workflow.ApplicationStatus;
import com.example.plms.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DocumentService {

    public static final Set<String> CATEGORIES =
            Set.of("IDENTITY_PROOF", "INCOME_PROOF", "BANK_STATEMENT", "ADDRESS_PROOF");
    private static final Set<String> DECISIONS = Set.of("VERIFIED", "REJECTED", "RESUBMISSION_REQUIRED");
    private static final Set<ApplicationStatus> UPLOAD_ALLOWED = EnumSet.of(ApplicationStatus.DRAFT,
            ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.RETURNED_FOR_CORRECTION);
    private static final Role[] STAFF_READERS = {Role.DOCUMENT_VERIFIER, Role.LOAN_OFFICER, Role.LOAN_APPROVER};

    private final DocumentMetadataRepository documents;
    private final DocumentVerificationRepository verifications;
    private final DocumentStorage storage;
    private final ApplicationService applications;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PlmsProperties.Documents config;
    private final Clock clock;

    public DocumentService(DocumentMetadataRepository documents, DocumentVerificationRepository verifications,
                           DocumentStorage storage, ApplicationService applications,
                           NotificationService notifications, AuditService audit, PlmsProperties properties,
                           Clock clock) {
        this.documents = documents;
        this.verifications = verifications;
        this.storage = storage;
        this.applications = applications;
        this.notifications = notifications;
        this.audit = audit;
        this.config = properties.documents();
        this.clock = clock;
    }

    public record Content(DocumentMetadata metadata, byte[] bytes) {
    }

    public record ChecklistItem(String category, boolean mandatory, String status, String documentId, Integer version,
                                String reviewerComment) {
    }

    @Transactional
    public DocumentMetadata upload(Actor actor, String applicationId, String category, MultipartFile file) {
        LoanApplication app = applications.getAccessible(actor, applicationId);
        if (!actor.isCustomer() || !app.getCustomerId().equals(actor.userId())) {
            throw ApiException.notFound("Application");
        }
        if (!UPLOAD_ALLOWED.contains(app.getStatus())) {
            throw ApiException.invalidState();
        }
        if (category == null || !CATEGORIES.contains(category)) {
            throw ApiException.badRequest("INVALID_CATEGORY", "Unknown document category.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Choose a file to upload.");
        }
        if (file.getSize() > config.maxBytes()) {
            throw ApiException.tooLarge("The file exceeds the maximum size of " + (config.maxBytes() / 1024 / 1024)
                    + " MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("UNREADABLE_FILE", "The file could not be read.");
        }
        FileInspector.Inspection inspection = FileInspector.inspect(file.getOriginalFilename(), bytes);
        if (!inspection.ok()) {
            switch (inspection.problem()) {
                case EMPTY -> throw ApiException.badRequest("EMPTY_FILE", "Choose a file to upload.");
                case PASSWORD_PROTECTED -> throw ApiException.badRequest("PASSWORD_PROTECTED_FILE",
                        "Password-protected files are not accepted.");
                default -> throw ApiException.unsupportedMedia(
                        "Only PDF, JPG and PNG files with valid content are accepted.");
            }
        }

        Instant now = Instant.now(clock);
        int nextVersion = 1;
        var previous = documents.findFirstByApplicationIdAndCategoryOrderByDocVersionDesc(applicationId, category);
        if (previous.isPresent()) {
            nextVersion = previous.get().getDocVersion() + 1;
            previous.get().setStatus(DocumentMetadata.Status.SUPERSEDED);
        }
        String key = storage.store(bytes);
        DocumentMetadata doc = documents.save(new DocumentMetadata(applicationId, app.getCustomerId(), category, key,
                FileInspector.safeFileName(file.getOriginalFilename()), inspection.kind().mimeType(), bytes.length,
                sha256(bytes), nextVersion, now));
        audit.record(actor.userId(), "DOCUMENT_UPLOADED", "DOCUMENT", doc.getId(), "SUCCESS",
                Map.of("applicationId", applicationId, "category", category, "version", nextVersion));
        return doc;
    }

    @Transactional
    public DocumentMetadata decide(String verifierId, String documentId, String decision, String reasonCode,
                                   String comment) {
        DocumentMetadata doc = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
        if (decision == null || !DECISIONS.contains(decision)) {
            throw ApiException.badRequest("INVALID_DECISION", "Decision must be VERIFIED, REJECTED or RESUBMISSION_REQUIRED.");
        }
        if (!"VERIFIED".equals(decision) && (reasonCode == null || reasonCode.isBlank())) {
            throw ApiException.badRequest("REASON_REQUIRED", "A reason code is required for this decision.");
        }
        if (!doc.is(DocumentMetadata.Status.PENDING_REVIEW)) {
            throw ApiException.conflict("DOCUMENT_ALREADY_DECIDED", "This document version has already been reviewed.");
        }
        if (doc.getCustomerId().equals(verifierId)) {
            throw ApiException.forbidden("SELF_VERIFICATION", "You cannot verify your own document.");
        }
        Instant now = Instant.now(clock);
        doc.setStatus(DocumentMetadata.Status.valueOf(decision));
        verifications.save(new DocumentVerification(documentId, decision, reasonCode, trim(comment), verifierId, now));
        audit.record(verifierId, "DOCUMENT_" + decision, "DOCUMENT", documentId, "SUCCESS",
                Map.of("reasonCode", String.valueOf(reasonCode), "version", doc.getDocVersion()));
        if (!"VERIFIED".equals(decision)) {
            notifications.notify(doc.getCustomerId(), "DOCUMENT_ACTION_REQUIRED", "Document action required",
                    "Please upload a new " + doc.getCategory().replace('_', ' ').toLowerCase()
                            + " for application " + ApplicationDtos.reference(doc.getApplicationId()) + ".");
        }
        return doc;
    }

    @Transactional(readOnly = true)
    public List<DocumentMetadata> forApplication(Actor actor, String applicationId) {
        applications.getAccessible(actor, applicationId);
        return documents.findByApplicationIdOrderByCategoryAscDocVersionDesc(applicationId);
    }

    @Transactional(readOnly = true)
    public List<ChecklistItem> checklist(Actor actor, String applicationId) {
        applications.getAccessible(actor, applicationId);
        Map<String, ChecklistItem> items = new LinkedHashMap<>();
        List<String> order = new ArrayList<>(config.mandatoryCategories());
        CATEGORIES.stream().sorted().filter(c -> !order.contains(c)).forEach(order::add);
        for (String category : order) {
            boolean mandatory = config.mandatoryCategories().contains(category);
            var latest = documents.findFirstByApplicationIdAndCategoryOrderByDocVersionDesc(applicationId, category);
            if (latest.isEmpty()) {
                items.put(category, new ChecklistItem(category, mandatory, "MISSING", null, null, null));
            } else {
                DocumentMetadata d = latest.get();
                String comment = verifications.findByDocumentIdOrderByDecidedAtAsc(d.getId()).stream()
                        .reduce((a, b) -> b).map(DocumentVerification::getComment).orElse(null);
                items.put(category, new ChecklistItem(category, mandatory, d.getStatus(), d.getId(),
                        d.getDocVersion(), comment));
            }
        }
        return List.copyOf(items.values());
    }

    /** True when the latest version of every mandatory category is VERIFIED. */
    @Transactional(readOnly = true)
    public List<String> missingMandatory(String applicationId) {
        List<String> missing = new ArrayList<>();
        for (String category : config.mandatoryCategories()) {
            boolean ok = documents.findFirstByApplicationIdAndCategoryOrderByDocVersionDesc(applicationId, category)
                    .map(d -> d.is(DocumentMetadata.Status.VERIFIED)).orElse(false);
            if (!ok) {
                missing.add(category);
            }
        }
        return missing;
    }

    /** Every read is authorised per request and logged. */
    @Transactional
    public Content content(Actor actor, String documentId) {
        DocumentMetadata doc = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
        boolean owner = actor.isCustomer() && doc.getCustomerId().equals(actor.userId());
        if (!owner && !actor.hasAny(STAFF_READERS)) {
            audit.recordIndependently(actor.userId(), "DOCUMENT_ACCESS_DENIED", "DOCUMENT", documentId, "DENIED",
                    Map.of());
            throw ApiException.notFound("Document");
        }
        audit.record(actor.userId(), "DOCUMENT_READ", "DOCUMENT", documentId, "SUCCESS", Map.of());
        return new Content(doc, storage.read(doc.getObjectKey()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentDtos.DocumentResponse> staffQueue(String status, Integer page, Integer size) {
        String s = status == null || status.isBlank() ? DocumentMetadata.Status.PENDING_REVIEW.name() : status;
        var pageable = PageResponse.pageable(page, size, null, Set.of("uploadedAt"), "uploadedAt");
        return PageResponse.of(documents.findByStatus(s, pageable), DocumentDtos.DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public List<DocumentVerification> verificationHistory(String documentId) {
        return verifications.findByDocumentIdOrderByDecidedAtAsc(documentId);
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
