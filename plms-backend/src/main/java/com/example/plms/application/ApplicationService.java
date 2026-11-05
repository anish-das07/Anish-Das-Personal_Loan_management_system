package com.example.plms.application;

import com.example.plms.audit.AuditService;
import com.example.plms.auth.Role;
import com.example.plms.common.Actor;
import com.example.plms.common.ApiError;
import com.example.plms.common.ApiException;
import com.example.plms.common.PageResponse;
import com.example.plms.core.eligibility.EligibilityEngine;
import com.example.plms.core.workflow.ApplicationStatus;
import com.example.plms.customer.CustomerProfile;
import com.example.plms.customer.CustomerService;
import com.example.plms.eligibility.EligibilityDtos;
import com.example.plms.eligibility.EligibilityService;
import com.example.plms.notification.NotificationService;
import com.example.plms.product.LoanProduct;
import com.example.plms.product.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Application use cases: draft, submit, track, and the guarded state transitions used by other modules. */
@Service
public class ApplicationService {

    public static final String SYSTEM_ACTOR = "SYSTEM";

    private static final Role[] STAFF_READ_ROLES = {Role.LOAN_OFFICER, Role.CREDIT_ANALYST, Role.DOCUMENT_VERIFIER,
            Role.LOAN_APPROVER, Role.FINANCE_OFFICER, Role.ADMIN};

    private final LoanApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final ProductService products;
    private final CustomerService customers;
    private final EligibilityService eligibility;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public ApplicationService(LoanApplicationRepository applications, StatusHistoryRepository history,
                              ProductService products, CustomerService customers, EligibilityService eligibility,
                              NotificationService notifications, AuditService audit, Clock clock) {
        this.applications = applications;
        this.history = history;
        this.products = products;
        this.customers = customers;
        this.eligibility = eligibility;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- access

    /**
     * Customers may only read their own applications; anything else is reported as not found so the
     * existence of other customers' records is not revealed. Staff roles may read for their work queues.
     */
    @Transactional(readOnly = true)
    public LoanApplication getAccessible(Actor actor, String applicationId) {
        LoanApplication app = applications.findById(applicationId).orElse(null);
        if (actor.hasAny(STAFF_READ_ROLES) && app != null) {
            return app;
        }
        if (actor.isCustomer() && app != null && app.getCustomerId().equals(actor.userId())) {
            return app;
        }
        if (app != null) {
            audit.recordIndependently(actor.userId(), "ACCESS_DENIED", "LOAN_APPLICATION", applicationId, "DENIED",
                    Map.of("reason", "NOT_OWNER"));
        }
        throw ApiException.notFound("Application");
    }

    /**
     * Locks the application row for the rest of the caller's transaction. Call it before any other read
     * of the same application in that transaction so the locked state is the freshly read one.
     */
    @Transactional
    public LoanApplication lockForUpdate(String applicationId) {
        return applications.findForUpdate(applicationId).orElseThrow(() -> ApiException.notFound("Application"));
    }

    @Transactional(readOnly = true)
    public LoanApplication get(String applicationId) {
        return applications.findById(applicationId).orElseThrow(() -> ApiException.notFound("Application"));
    }

    private LoanApplication owned(String customerId, String applicationId) {
        LoanApplication app = applications.findById(applicationId).orElse(null);
        if (app == null || !app.getCustomerId().equals(customerId)) {
            if (app != null) {
                audit.recordIndependently(customerId, "ACCESS_DENIED", "LOAN_APPLICATION", applicationId, "DENIED",
                        Map.of("reason", "NOT_OWNER"));
            }
            throw ApiException.notFound("Application");
        }
        return app;
    }

    // ---------------------------------------------------------------- customer use cases

    @Transactional
    public LoanApplication createDraft(String customerId, ApplicationDtos.CreateRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = applications.findByCustomerIdAndCreateIdempotencyKey(customerId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        LoanProduct product = products.getAvailable(request.productId());
        checkLimits(product, request.requestedAmount(), request.requestedMonths());
        Instant now = Instant.now(clock);
        LoanApplication app = applications.save(LoanApplication.draft(customerId, product, request,
                blankToNull(idempotencyKey), now));
        history.save(new ApplicationStatusHistory(app.getId(), null, ApplicationStatus.DRAFT.name(), customerId,
                "DRAFT_CREATED", "Application draft created", now));
        audit.record(customerId, "APPLICATION_DRAFT_CREATED", "LOAN_APPLICATION", app.getId(), "SUCCESS",
                Map.of("productId", product.getId()));
        return app;
    }

    @Transactional
    public LoanApplication updateDraft(String customerId, String applicationId, ApplicationDtos.UpdateRequest request) {
        LoanApplication app = owned(customerId, applicationId);
        if (!app.getStatus().isEditableByCustomer()) {
            throw ApiException.invalidState();
        }
        LoanProduct product = request.productId() != null ? products.getAvailable(request.productId())
                : products.get(app.getProductId());
        app.applyPatch(request, request.productId() != null ? product : null, Instant.now(clock));
        checkLimits(product, app.getRequestedAmount(), app.getRequestedMonths());
        return app;
    }

    @Transactional
    public LoanApplication submit(String customerId, String applicationId, String idempotencyKey,
                                  Boolean declarationAccepted) {
        LoanApplication app = owned(customerId, applicationId);
        if (idempotencyKey != null && idempotencyKey.equals(app.getSubmitIdempotencyKey())
                && !app.getStatus().isEditableByCustomer()) {
            return app; // replay of the same submission: return the existing result
        }
        if (!app.getStatus().isEditableByCustomer()) {
            throw ApiException.invalidState();
        }
        if (!Boolean.TRUE.equals(declarationAccepted)) {
            throw ApiException.badRequest("DECLARATION_REQUIRED",
                    "Confirm the information is accurate and consent to assessment.");
        }
        LoanProduct product = products.getAvailable(app.getProductId());
        checkLimits(product, app.getRequestedAmount(), app.getRequestedMonths());
        List<ApiError.FieldError> missing = new ArrayList<>();
        if (app.getMonthlyIncome() == null || app.getMonthlyIncome().signum() <= 0) {
            missing.add(new ApiError.FieldError("monthlyIncome", "Monthly income is required"));
        }
        if (app.getEmploymentType() == null) {
            missing.add(new ApiError.FieldError("employmentType", "Employment type is required"));
        }
        if (!missing.isEmpty()) {
            throw new ApiException(400, "VALIDATION_FAILED", "Complete the required sections before submitting.",
                    missing);
        }
        CustomerProfile profile = customers.get(customerId);
        if (!profile.isReadyForApplication()) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE",
                    "Complete your personal and address details before submitting.");
        }

        Instant now = Instant.now(clock);
        app.snapshotTerms(product, now);
        app.markSubmitKey(blankToNull(idempotencyKey));
        move(app, ApplicationStatus.SUBMITTED, customerId, "SUBMITTED", "Application submitted");

        EligibilityService.Evaluation evaluation = eligibility.evaluate(command(app, profile), SYSTEM_ACTOR);
        app.recordEligibility(evaluation.outcome().name(), now);
        if (evaluation.outcome() == EligibilityEngine.Outcome.NOT_ELIGIBLE && evaluation.autoRejectOnFail()) {
            move(app, ApplicationStatus.REJECTED, SYSTEM_ACTOR, "ELIGIBILITY_NOT_MET",
                    "The application does not meet the product's eligibility criteria");
            notifications.notify(customerId, "APPLICATION_DECIDED", "Application update",
                    "Your application " + ApplicationDtos.reference(app.getId()) + " was not approved.");
        } else {
            move(app, ApplicationStatus.UNDER_REVIEW, SYSTEM_ACTOR, "ASSESSMENT_STARTED",
                    "Your application is being reviewed");
            notifications.notify(customerId, "APPLICATION_SUBMITTED", "Application submitted",
                    "We received application " + ApplicationDtos.reference(app.getId()) + ".");
        }
        return app;
    }

    @Transactional
    public LoanApplication withdraw(String customerId, String applicationId) {
        LoanApplication app = owned(customerId, applicationId);
        move(app, ApplicationStatus.WITHDRAWN, customerId, "WITHDRAWN_BY_CUSTOMER", "Application withdrawn");
        return app;
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> listMine(String customerId) {
        return applications.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusHistory> history(Actor actor, String applicationId) {
        getAccessible(actor, applicationId);
        return history.findByApplicationIdOrderByOccurredAtAsc(applicationId);
    }

    // ---------------------------------------------------------------- staff use cases

    @Transactional(readOnly = true)
    public PageResponse<ApplicationDtos.QueueRow> queue(String queue, Integer page, Integer size) {
        Set<ApplicationStatus> statuses = switch (queue == null ? "REVIEW" : queue) {
            case "NEW" -> EnumSet.of(ApplicationStatus.SUBMITTED);
            case "REVIEW" -> EnumSet.of(ApplicationStatus.UNDER_REVIEW);
            case "RETURNED" -> EnumSet.of(ApplicationStatus.RETURNED_FOR_CORRECTION);
            case "DECIDED" -> EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);
            case "FUNDING" -> EnumSet.of(ApplicationStatus.APPROVED, ApplicationStatus.DISBURSEMENT_PENDING);
            case "ACTIVE" -> EnumSet.of(ApplicationStatus.ACTIVE);
            case "ALL" -> EnumSet.complementOf(EnumSet.of(ApplicationStatus.DRAFT));
            default -> throw ApiException.badRequest("INVALID_QUEUE", "Unknown queue.");
        };
        var pageable = PageResponse.pageable(page, size, null, Set.of("submittedAt"), "submittedAt");
        Page<LoanApplication> result = applications.findByStatusIn(statuses, pageable);
        return PageResponse.of(result, ApplicationDtos.QueueRow::from);
    }

    @Transactional
    public LoanApplication assignToSelf(String officerId, String applicationId) {
        LoanApplication app = get(applicationId);
        if (app.getStatus() != ApplicationStatus.UNDER_REVIEW && app.getStatus() != ApplicationStatus.SUBMITTED) {
            throw ApiException.invalidState();
        }
        app.assignTo(officerId, Instant.now(clock));
        audit.record(officerId, "APPLICATION_ASSIGNED", "LOAN_APPLICATION", applicationId, "SUCCESS", Map.of());
        return app;
    }

    /** Re-runs eligibility (e.g. after a provider outage) and records the new outcome. */
    @Transactional
    public EligibilityService.Evaluation reevaluate(String actorId, String applicationId, String policyVersion) {
        LoanApplication app = get(applicationId);
        if (app.getStatus() != ApplicationStatus.UNDER_REVIEW && app.getStatus() != ApplicationStatus.SUBMITTED) {
            throw ApiException.invalidState();
        }
        CustomerProfile profile = customers.get(app.getCustomerId());
        EligibilityDtos.EvaluationCommand base = command(app, profile);
        EligibilityDtos.EvaluationCommand cmd = policyVersion == null || policyVersion.isBlank() ? base
                : new EligibilityDtos.EvaluationCommand(base.applicationId(), base.customerId(), policyVersion,
                base.requestedAmount(), base.requestedMonths(), base.minAmount(), base.maxAmount(),
                base.minMonths(), base.maxMonths(), base.annualRate(), base.monthlyIncome(),
                base.existingMonthlyObligations(), base.employmentType(), base.employmentMonths(), base.ageYears());
        EligibilityService.Evaluation evaluation = eligibility.evaluate(cmd, actorId);
        app.recordEligibility(evaluation.outcome().name(), Instant.now(clock));
        return evaluation;
    }

    /**
     * Guarded transition used by the approval, disbursement and closure modules. If an expected version
     * is supplied and differs from the current one, the caller acted on stale data and gets 409.
     */
    @Transactional
    public LoanApplication transition(String applicationId, Long expectedVersion, ApplicationStatus to,
                                      String actorId, String reasonCode, String customerMessage) {
        LoanApplication app = get(applicationId);
        if (expectedVersion != null && !Objects.equals(expectedVersion, app.getVersion())) {
            throw ApiException.conflict("STALE_STATE", "The application changed since it was loaded. Reload and retry.");
        }
        move(app, to, actorId, reasonCode, customerMessage);
        applications.flush();
        return app;
    }

    // ---------------------------------------------------------------- helpers

    private void move(LoanApplication app, ApplicationStatus to, String actorId, String reasonCode, String message) {
        Instant now = Instant.now(clock);
        if (!app.getStatus().canTransitionTo(to)) {
            throw ApiException.invalidState();
        }
        ApplicationStatus from = app.transitionTo(to, now);
        history.save(new ApplicationStatusHistory(app.getId(), from.name(), to.name(), actorId, reasonCode, message,
                now));
        audit.record(actorId, "APPLICATION_" + to.name(), "LOAN_APPLICATION", app.getId(), "SUCCESS",
                Map.of("from", from.name(), "reason", String.valueOf(reasonCode)));
    }

    private EligibilityDtos.EvaluationCommand command(LoanApplication app, CustomerProfile profile) {
        var start = app.getEmploymentStartDate() != null ? app.getEmploymentStartDate()
                : profile.getEmploymentStartDate();
        return new EligibilityDtos.EvaluationCommand(app.getId(), app.getCustomerId(), app.getPolicyVersion(),
                app.getRequestedAmount(), app.getRequestedMonths(), app.getSnapshotMinAmount(),
                app.getSnapshotMaxAmount(), app.getSnapshotMinMonths(), app.getSnapshotMaxMonths(),
                app.getSnapshotAnnualRate(), app.getMonthlyIncome(), app.getExistingMonthlyObligations(),
                app.getEmploymentType(), customers.employmentMonths(start), customers.ageYears(profile));
    }

    private static void checkLimits(LoanProduct product, java.math.BigDecimal amount, int months) {
        if (amount.compareTo(product.getMinAmount()) < 0 || amount.compareTo(product.getMaxAmount()) > 0
                || months < product.getMinMonths() || months > product.getMaxMonths()) {
            throw new ApiException(400, "OUTSIDE_PRODUCT_LIMITS",
                    "Requested amount or tenure is outside the product limits.",
                    List.of(new ApiError.FieldError("requestedAmount",
                                    "Allowed " + product.getMinAmount() + " - " + product.getMaxAmount()),
                            new ApiError.FieldError("requestedMonths",
                                    "Allowed " + product.getMinMonths() + " - " + product.getMaxMonths())));
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
