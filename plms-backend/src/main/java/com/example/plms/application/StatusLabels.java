package com.example.plms.application;

import com.example.plms.core.workflow.ApplicationStatus;

/** Maps internal statuses to customer-safe labels and next actions. */
public final class StatusLabels {

    private StatusLabels() {
    }

    public static String label(ApplicationStatus s) {
        return switch (s) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
            case UNDER_REVIEW -> "Under review";
            case RETURNED_FOR_CORRECTION -> "Returned for correction";
            case APPROVED -> "Approved";
            case REJECTED -> "Not approved";
            case DISBURSEMENT_PENDING -> "Disbursement in progress";
            case ACTIVE -> "Active loan";
            case CLOSED -> "Closed";
            case WITHDRAWN -> "Withdrawn";
        };
    }

    public static String nextAction(ApplicationStatus s) {
        return switch (s) {
            case DRAFT -> "Complete required sections and submit";
            case SUBMITTED, UNDER_REVIEW -> "Upload any requested documents and wait for an update";
            case RETURNED_FOR_CORRECTION -> "Review the requested corrections and resubmit";
            case APPROVED, DISBURSEMENT_PENDING -> "Wait for disbursement confirmation";
            case ACTIVE -> "View your repayment schedule";
            case REJECTED, CLOSED, WITHDRAWN -> "No action required";
        };
    }
}
