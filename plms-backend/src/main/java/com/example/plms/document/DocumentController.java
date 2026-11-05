package com.example.plms.document;

import com.example.plms.common.Actor;
import com.example.plms.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<DocumentDtos.DocumentResponse> upload(@RequestParam String applicationId,
                                                                @RequestParam String category,
                                                                @RequestPart("file") MultipartFile file,
                                                                Authentication auth) {
        DocumentMetadata doc = service.upload(Actor.from(auth), applicationId, category, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentDtos.DocumentResponse.from(doc));
    }

    @PostMapping("/api/v1/documents/{id}/decision")
    @PreAuthorize("hasRole('DOCUMENT_VERIFIER')")
    public DocumentDtos.DocumentResponse decide(@PathVariable String id,
                                                @Valid @RequestBody DocumentDtos.DecisionRequest request,
                                                Authentication auth) {
        return DocumentDtos.DocumentResponse.from(service.decide(Actor.from(auth).userId(), id, request.decision(),
                request.reasonCode(), request.comment()));
    }

    @GetMapping("/api/v1/applications/{applicationId}/documents")
    public List<DocumentDtos.DocumentResponse> list(@PathVariable String applicationId, Authentication auth) {
        return service.forApplication(Actor.from(auth), applicationId).stream()
                .map(DocumentDtos.DocumentResponse::from).toList();
    }

    @GetMapping("/api/v1/applications/{applicationId}/documents/checklist")
    public List<DocumentService.ChecklistItem> checklist(@PathVariable String applicationId, Authentication auth) {
        return service.checklist(Actor.from(auth), applicationId);
    }

    /** Authorised, logged, served as an attachment with no-sniff so it is never rendered inline. */
    @GetMapping("/api/v1/documents/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable String id, Authentication auth) {
        DocumentService.Content content = service.content(Actor.from(auth), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.metadata().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.metadata().getFileNameSafe()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content.bytes());
    }

    @GetMapping("/api/v1/documents/{id}/verifications")
    @PreAuthorize("hasAnyRole('DOCUMENT_VERIFIER','LOAN_APPROVER','LOAN_OFFICER','ADMIN')")
    public List<DocumentDtos.VerificationView> verifications(@PathVariable String id) {
        return service.verificationHistory(id).stream().map(DocumentDtos.VerificationView::from).toList();
    }

    @GetMapping("/api/v1/staff/documents")
    @PreAuthorize("hasRole('DOCUMENT_VERIFIER')")
    public PageResponse<DocumentDtos.DocumentResponse> queue(@RequestParam(required = false) String status,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return service.staffQueue(status, page, size);
    }
}
