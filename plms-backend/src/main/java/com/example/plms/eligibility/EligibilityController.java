package com.example.plms.eligibility;

import com.example.plms.application.ApplicationService;
import com.example.plms.common.Actor;
import com.example.plms.common.ApiException;
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

import java.util.List;

@RestController
public class EligibilityController {

    private final EligibilityService eligibility;
    private final ApplicationService applications;

    public EligibilityController(EligibilityService eligibility, ApplicationService applications) {
        this.eligibility = eligibility;
        this.applications = applications;
    }

    /** Re-evaluates an application (internal/authorised staff). */
    @PostMapping("/api/v1/eligibility/evaluations")
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','LOAN_OFFICER')")
    public EligibilityDtos.AssessmentResponse evaluate(@Valid @RequestBody EligibilityDtos.EvaluateRequest request,
                                                       Authentication auth) {
        return EligibilityService.toResponse(applications.reevaluate(Actor.from(auth).userId(),
                request.applicationId(), request.policyVersion()));
    }

    @GetMapping("/api/v1/eligibility/assessments/{assessmentId}")
    public Object byId(@PathVariable String assessmentId, Authentication auth) {
        Actor actor = Actor.from(auth);
        EligibilityService.Evaluation evaluation = eligibility.byId(assessmentId);
        applications.getAccessible(actor, evaluation.assessment().getApplicationId());
        return actor.isStaff() ? EligibilityService.toResponse(evaluation)
                : EligibilityService.toCustomerView(evaluation);
    }

    /** Latest assessment for an application; customers get the customer-safe view. */
    @GetMapping("/api/v1/applications/{applicationId}/eligibility")
    public Object latestForApplication(@PathVariable String applicationId, Authentication auth) {
        Actor actor = Actor.from(auth);
        applications.getAccessible(actor, applicationId);
        EligibilityService.Evaluation evaluation = eligibility.latest(applicationId)
                .orElseThrow(() -> ApiException.notFound("Assessment"));
        return actor.isStaff() ? EligibilityService.toResponse(evaluation)
                : EligibilityService.toCustomerView(evaluation);
    }

    @GetMapping("/api/v1/admin/eligibility-policies")
    @PreAuthorize("hasAnyRole('ADMIN','CREDIT_ANALYST')")
    public List<EligibilityDtos.PolicyResponse> policies() {
        return eligibility.listPolicies().stream().map(EligibilityDtos.PolicyResponse::from).toList();
    }

    @PostMapping("/api/v1/admin/eligibility-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EligibilityDtos.PolicyResponse> createPolicy(
            @Valid @RequestBody EligibilityDtos.PolicyRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(EligibilityDtos.PolicyResponse.from(
                eligibility.createPolicy(Actor.from(auth).userId(), request)));
    }
}
