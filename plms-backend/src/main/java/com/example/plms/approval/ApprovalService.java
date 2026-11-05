package com.example.plms.approval;

import com.example.plms.account.LoanAccount;
import com.example.plms.account.LoanAccountService;
import com.example.plms.application.ApplicationDtos;
import com.example.plms.application.ApplicationService;
import com.example.plms.application.LoanApplication;
import com.example.plms.audit.AuditService;
import com.example.plms.auth.Role;
import com.example.plms.common.Actor;
import com.example.plms.common.ApiError;
import com.example.plms.common.ApiException;
import com.example.plms.common.PlmsProperties;
import com.example.plms.core.workflow.ApplicationStatus;
import com.example.plms.credit.CreditAssessmentService;
import com.example.plms.customer.CustomerDtos;
import com.example.plms.customer.CustomerService;
import com.example.plms.document.DocumentService;
import com.example.plms.eligibility.EligibilityService;
import com.example.plms.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Review and decision workflow. Enforces role, state, optimistic version, maker-checker and evidence
 * completeness on the server; every decision is persisted as immutable history and audited.
 */
@Service
public class ApprovalService {

    private static final Set<String> BLOCKING_ELIGIBILITY = Set.of("NOT_ELIGIBLE", "PENDING_PROVIDER");

    private final ApplicationService applications;
    private final LoanReviewRepository reviews;
    private final LoanDecisionRepository decisions;
    private final LoanAccountService accounts;
    private final DocumentService documents;
    private final EligibilityService eligibility;
    private final CreditAssessmentService credit;
    private final CustomerService customers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PlmsProperties.Approval config;
    private final Clock clock;

    public ApprovalService(ApplicationService applications, LoanReviewRepository reviews,
                           LoanDecisionRepository decisions, LoanAccountService accounts, DocumentService documents,
                           EligibilityService eligibility, CreditAssessmentService credit, CustomerService customers,
                           NotificationService notifications, AuditService audit, PlmsProperties properties,
                           Clock clock) {
        this.applications = applications;
        this.reviews = reviews;
        this.decisions = decisions;
        this.accounts = accounts;
        this.documents = documents;
        this.eligibility = eligibility;
        this.credit = credit;
        this.customers = customers;
        this.notifications = notifications;
        this.audit = audit;
        this.config = properties.approval();
        this.clock = clock;
    }

    public record DecisionResult(LoanDecision decision, LoanApplication application, LoanAccount account) {
    }

    @Transactional
    public LoanReview recommend(Actor actor, String applicationId, ApprovalDtos.RecommendationRequest request) {
        LoanApplication app = applications.lockForUpdate(applicationId);
        if (app.getStatus() != ApplicationStatus.UNDER_REVIEW) {
            throw ApiException.invalidState();
        }
        if (actor.has(Role.LOAN_OFFICER) && app.getAssignedOfficerId() != null
                && !app.getAssignedOfficerId().equals(actor.userId())) {
            throw ApiException.forbidden("NOT_ASSIGNED", "This application is assigned to another officer.");
        }
        String role = actor.has(Role.LOAN_OFFICER) ? Role.LOAN_OFFICER.name() : Role.CREDIT_ANALYST.name();
        LoanReview review = reviews.save(new LoanReview(applicationId, actor.userId(), role,
                request.recommendation(), request.reasonCode(), trim(request.comment()), Instant.now(clock)));
        audit.record(actor.userId(), "RECOMMENDATION_RECORDED", "LOAN_APPLICATION", applicationId, "SUCCESS",
                Map.of("recommendation", request.recommendation(), "reasonCode", request.reasonCode()));
        return review;
    }

    @Transactional
    public DecisionResult decide(String approverId, String applicationId, ApprovalDtos.DecisionRequest request) {
        // Lock first so concurrent decisions are serialised and only one terminal decision succeeds.
        LoanApplication app = applications.lockForUpdate(applicationId);
        if (app.getStatus() != ApplicationStatus.UNDER_REVIEW) {
            throw ApiException.invalidState();
        }
        if (request.expectedVersion() != null && !request.expectedVersion().equals(app.getVersion())) {
            throw ApiException.conflict("STALE_STATE", "The application changed since it was loaded. Reload and retry.");
        }
        List<LoanReview> recs = reviews.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        if (config.makerChecker() && recs.stream().anyMatch(r -> r.getReviewerId().equals(approverId))) {
            audit.recordIndependently(approverId, "MAKER_CHECKER_VIOLATION", "LOAN_APPLICATION", applicationId,
                    "DENIED", Map.of());
            throw ApiException.forbidden("MAKER_CHECKER_VIOLATION",
                    "You recommended this application, so a different approver must decide it.");
        }
        if (app.getCustomerId().equals(approverId)) {
            throw ApiException.forbidden("SELF_APPROVAL", "You cannot decide your own application.");
        }

        Instant now = Instant.now(clock);
        LoanAccount account = null;
        BigDecimal amount = null;
        Integer months = null;
        ApplicationStatus target;
        String message;
        switch (request.decision()) {
            case "APPROVE" -> {
                if (config.requireRecommendation() && recs.isEmpty()) {
                    throw ApiException.conflict("RECOMMENDATION_REQUIRED",
                            "A loan officer or credit analyst recommendation is required before a decision.");
                }
                amount = request.approvedAmount() == null ? app.getRequestedAmount() : request.approvedAmount();
                months = request.approvedMonths() == null ? app.getRequestedMonths() : request.approvedMonths();
                validateApprovedTerms(app, amount, months);
                if (app.getEligibilityOutcome() == null || BLOCKING_ELIGIBILITY.contains(app.getEligibilityOutcome())) {
                    throw ApiException.conflict("ELIGIBILITY_INCOMPLETE",
                            "Eligibility must be ELIGIBLE or MANUAL_REVIEW before approval. Re-run the assessment if the provider was unavailable.");
                }
                List<String> missing = documents.missingMandatory(applicationId);
                if (!missing.isEmpty()) {
                    throw new ApiException(409, "DOCUMENTS_INCOMPLETE", "Mandatory documents are not verified.",
                            missing.stream().map(c -> new ApiError.FieldError(c, "Not verified")).toList());
                }
                target = ApplicationStatus.APPROVED;
                message = "Your application was approved";
            }
            case "REJECT" -> {
                target = ApplicationStatus.REJECTED;
                message = "Your application was not approved";
            }
            case "RETURN_FOR_CORRECTION" -> {
                target = ApplicationStatus.RETURNED_FOR_CORRECTION;
                message = "Your application needs corrections before review can continue";
            }
            default -> throw ApiException.badRequest("INVALID_DECISION", "Unsupported decision.");
        }

        LoanDecision decision = decisions.save(new LoanDecision(applicationId, request.decision(), amount, months,
                approverId, request.reasonCode(), trim(request.comment()), app.getPolicyVersion(), now));
        applications.transition(applicationId, null, target, approverId, request.reasonCode(), message);
        if (target == ApplicationStatus.APPROVED) {
            account = accounts.openPending(applicationId, app.getCustomerId(), app.getProductName(), amount,
                    app.getSnapshotAnnualRate(), months, app.getSnapshotFeePercent(), approverId);
        }
        audit.record(approverId, "LOAN_DECISION_" + request.decision(), "LOAN_APPLICATION", applicationId, "SUCCESS",
                Map.of("reasonCode", request.reasonCode(), "approvedAmount", String.valueOf(amount)));
        notifications.notify(app.getCustomerId(), "APPLICATION_DECIDED", "Application update",
                message + " (" + ApplicationDtos.reference(applicationId) + ").");
        return new DecisionResult(decision, app, account);
    }

    @Transactional
    public ApprovalDtos.ReviewSummary summary(Actor actor, String applicationId) {
        LoanApplication app = applications.getAccessible(actor, applicationId);
        boolean includeScore = actor.hasAny(Role.CREDIT_ANALYST, Role.LOAN_APPROVER);
        Object creditView = credit.latest(applicationId).map(c -> Map.of(
                "status", c.getStatus(),
                "scoreBand", String.valueOf(c.getScoreBand()),
                "score", includeScore && c.getScore() != null ? c.getScore().toString() : "restricted",
                "source", "SYNTHETIC-BUREAU (synthetic)",
                "assessedAt", c.getAssessedAt().toString())).orElse(null);
        return new ApprovalDtos.ReviewSummary(
                ApplicationDtos.ApplicationResponse.from(app),
                CustomerDtos.StaffProfileView.from(customers.getForStaff(actor.userId(), app.getCustomerId())),
                eligibility.latest(applicationId).map(EligibilityService::toResponse).orElse(null),
                creditView,
                documents.checklist(actor, applicationId),
                documents.missingMandatory(applicationId),
                reviews.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                        .map(ApprovalDtos.ReviewView::from).toList(),
                decisions.findByApplicationIdOrderByDecidedAtAsc(applicationId).stream()
                        .map(d -> ApprovalDtos.DecisionView.from(d, null, null)).toList());
    }

    private static void validateApprovedTerms(LoanApplication app, BigDecimal amount, int months) {
        boolean ok = amount.compareTo(app.getRequestedAmount()) <= 0
                && amount.compareTo(app.getSnapshotMinAmount()) >= 0
                && amount.compareTo(app.getSnapshotMaxAmount()) <= 0
                && months >= app.getSnapshotMinMonths() && months <= app.getSnapshotMaxMonths();
        if (!ok) {
            throw ApiException.badRequest("OUTSIDE_PRODUCT_LIMITS",
                    "Approved amount must not exceed the requested amount and must be within product policy.");
        }
    }

    private static String trim(String s) {
        return s == null ? null : (s.length() > 500 ? s.substring(0, 500) : s.trim());
    }
}
