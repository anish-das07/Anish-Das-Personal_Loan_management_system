package com.example.plms.application;

import com.example.plms.common.Actor;
import com.example.plms.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/applications")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApplicationDtos.CreatedResponse> create(
            @Valid @RequestBody ApplicationDtos.CreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        LoanApplication app = service.createDraft(Actor.from(auth).userId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApplicationDtos.CreatedResponse(app.getId(),
                app.getStatus(), "Application draft created", StatusLabels.nextAction(app.getStatus())));
    }

    @PatchMapping("/api/v1/applications/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApplicationDtos.ApplicationResponse update(@PathVariable String id,
                                                      @Valid @RequestBody ApplicationDtos.UpdateRequest request,
                                                      Authentication auth) {
        return ApplicationDtos.ApplicationResponse.from(service.updateDraft(Actor.from(auth).userId(), id, request));
    }

    @PostMapping("/api/v1/applications/{id}/submit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApplicationDtos.ApplicationResponse submit(
            @PathVariable String id,
            @RequestBody(required = false) ApplicationDtos.SubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        return ApplicationDtos.ApplicationResponse.from(service.submit(Actor.from(auth).userId(), id, idempotencyKey,
                request == null ? null : request.declarationAccepted()));
    }

    @PostMapping("/api/v1/applications/{id}/withdraw")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApplicationDtos.ApplicationResponse withdraw(@PathVariable String id, Authentication auth) {
        return ApplicationDtos.ApplicationResponse.from(service.withdraw(Actor.from(auth).userId(), id));
    }

    @GetMapping("/api/v1/applications")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ApplicationDtos.ApplicationResponse> mine(Authentication auth) {
        return service.listMine(Actor.from(auth).userId()).stream()
                .map(ApplicationDtos.ApplicationResponse::from).toList();
    }

    @GetMapping("/api/v1/applications/{id}")
    public ApplicationDtos.ApplicationResponse get(@PathVariable String id, Authentication auth) {
        return ApplicationDtos.ApplicationResponse.from(service.getAccessible(Actor.from(auth), id));
    }

    @GetMapping("/api/v1/applications/{id}/history")
    public List<ApplicationDtos.HistoryEntry> history(@PathVariable String id, Authentication auth) {
        return service.history(Actor.from(auth), id).stream().map(ApplicationDtos.HistoryEntry::from).toList();
    }

    // ---- staff ----

    @GetMapping("/api/v1/staff/applications")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','CREDIT_ANALYST','LOAN_APPROVER','FINANCE_OFFICER','ADMIN')")
    public PageResponse<ApplicationDtos.QueueRow> queue(@RequestParam(required = false) String queue,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size) {
        return service.queue(queue, page, size);
    }

    @PostMapping("/api/v1/applications/{id}/assign")
    @PreAuthorize("hasRole('LOAN_OFFICER')")
    public ApplicationDtos.QueueRow assign(@PathVariable String id, Authentication auth) {
        return ApplicationDtos.QueueRow.from(service.assignToSelf(Actor.from(auth).userId(), id));
    }
}
