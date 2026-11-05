package com.example.plms.credit;

import com.example.plms.application.ApplicationService;
import com.example.plms.application.LoanApplication;
import com.example.plms.auth.Role;
import com.example.plms.common.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class CreditController {

    private final CreditAssessmentService credit;
    private final ApplicationService applications;
    private final SyntheticCreditBureau bureau;

    public CreditController(CreditAssessmentService credit, ApplicationService applications,
                            SyntheticCreditBureau bureau) {
        this.credit = credit;
        this.applications = applications;
        this.bureau = bureau;
    }

    public record AssessRequest(@NotBlank String applicationId) {
    }

    /** Raw score is visible only to roles that need it; customers see status and band. */
    public record CreditView(String id, String applicationId, String source, String status, Integer score,
                             String scoreBand, String failureReason, Instant assessedAt) {
        static CreditView from(CreditAssessment c, boolean includeScore) {
            return new CreditView(c.getId(), c.getApplicationId(), c.getProviderCode() + " (synthetic)",
                    c.getStatus(), includeScore ? c.getScore() : null, c.getScoreBand(),
                    includeScore ? c.getFailureReason() : null, c.getAssessedAt());
        }
    }

    public record ModeRequest(SyntheticCreditBureau.Mode mode) {
    }

    @PostMapping("/api/v1/credit-assessments")
    @PreAuthorize("hasRole('CREDIT_ANALYST')")
    public ResponseEntity<CreditView> assess(@Valid @RequestBody AssessRequest request, Authentication auth) {
        LoanApplication app = applications.get(request.applicationId());
        CreditAssessment result = credit.assess(app.getId(), app.getCustomerId(), Actor.from(auth).userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CreditView.from(result, true));
    }

    @GetMapping("/api/v1/credit-assessments")
    public List<CreditView> list(@RequestParam String applicationId, Authentication auth) {
        Actor actor = Actor.from(auth);
        applications.getAccessible(actor, applicationId);
        boolean includeScore = actor.hasAny(Role.CREDIT_ANALYST, Role.LOAN_APPROVER);
        return credit.forApplication(applicationId).stream().map(c -> CreditView.from(c, includeScore)).toList();
    }

    /** Demo/test switch for the simulated provider (normal, timeout, unavailable, malformed). */
    @PutMapping("/api/v1/admin/simulations/credit-provider")
    @PreAuthorize("hasRole('ADMIN')")
    public ModeRequest setMode(@RequestBody ModeRequest request) {
        bureau.setMode(request.mode());
        return new ModeRequest(bureau.mode());
    }
}
