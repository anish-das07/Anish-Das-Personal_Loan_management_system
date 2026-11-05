package com.example.plms.core.workflow;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Loan application state model (case study section 5).
 *
 * <pre>
 * DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED -> DISBURSEMENT_PENDING -> ACTIVE -> CLOSED
 *                            |-> REJECTED
 *                            |-> RETURNED_FOR_CORRECTION -> SUBMITTED (new version)
 * DRAFT / SUBMITTED / RETURNED_FOR_CORRECTION -> WITHDRAWN (customer, if policy permits)
 * SUBMITTED -> REJECTED (automatic, when the eligibility policy auto-rejects failed rules)
 * </pre>
 */
public enum ApplicationStatus {
    DRAFT, SUBMITTED, UNDER_REVIEW, RETURNED_FOR_CORRECTION, APPROVED, REJECTED,
    DISBURSEMENT_PENDING, ACTIVE, CLOSED, WITHDRAWN;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED =
            new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(SUBMITTED, WITHDRAWN));
        ALLOWED.put(SUBMITTED, EnumSet.of(UNDER_REVIEW, REJECTED, WITHDRAWN));
        ALLOWED.put(UNDER_REVIEW, EnumSet.of(APPROVED, REJECTED, RETURNED_FOR_CORRECTION));
        ALLOWED.put(RETURNED_FOR_CORRECTION, EnumSet.of(SUBMITTED, WITHDRAWN));
        ALLOWED.put(APPROVED, EnumSet.of(DISBURSEMENT_PENDING));
        ALLOWED.put(DISBURSEMENT_PENDING, EnumSet.of(ACTIVE));
        ALLOWED.put(ACTIVE, EnumSet.of(CLOSED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(CLOSED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
    }

    public Set<ApplicationStatus> allowedNext() {
        return Collections.unmodifiableSet(ALLOWED.get(this));
    }

    public boolean canTransitionTo(ApplicationStatus next) {
        return next != null && ALLOWED.get(this).contains(next);
    }

    public void assertTransition(ApplicationStatus next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateTransitionException(this, next);
        }
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    public boolean isEditableByCustomer() {
        return this == DRAFT || this == RETURNED_FOR_CORRECTION;
    }
}
