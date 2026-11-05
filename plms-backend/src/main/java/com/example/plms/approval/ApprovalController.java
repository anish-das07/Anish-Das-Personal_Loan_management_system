package com.example.plms.approval;

import com.example.plms.common.Actor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/applications/{id}/recommendation")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','CREDIT_ANALYST')")
    public ResponseEntity<ApprovalDtos.ReviewView> recommend(@PathVariable String id,
                                                             @Valid @RequestBody ApprovalDtos.RecommendationRequest request,
                                                             Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApprovalDtos.ReviewView.from(service.recommend(Actor.from(auth), id, request)));
    }

    /** Only LOAN_APPROVER can decide; ADMIN has no routine approval authority. */
    @PostMapping("/api/v1/applications/{id}/decision")
    @PreAuthorize("hasRole('LOAN_APPROVER')")
    public ApprovalDtos.DecisionView decide(@PathVariable String id,
                                            @Valid @RequestBody ApprovalDtos.DecisionRequest request,
                                            Authentication auth) {
        ApprovalService.DecisionResult result = service.decide(Actor.from(auth).userId(), id, request);
        return ApprovalDtos.DecisionView.from(result.decision(), result.application().getStatus().name(),
                result.account() == null ? null : result.account().getId());
    }

    @GetMapping("/api/v1/staff/applications/{id}/review")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','CREDIT_ANALYST','LOAN_APPROVER')")
    public ApprovalDtos.ReviewSummary review(@PathVariable String id, Authentication auth) {
        return service.summary(Actor.from(auth), id);
    }
}
