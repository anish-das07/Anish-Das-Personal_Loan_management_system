/**
 * Status semantics: every status has a text label plus a tone. Colour is never the only signal;
 * badges always render the label text.
 */
const STATUS = {
  DRAFT: ["Draft", "secondary"],
  SUBMITTED: ["Submitted", "info"],
  UNDER_REVIEW: ["Under review", "info"],
  RETURNED_FOR_CORRECTION: ["Returned for correction", "warning"],
  APPROVED: ["Approved", "success"],
  REJECTED: ["Not approved", "danger"],
  DISBURSEMENT_PENDING: ["Disbursement in progress", "info"],
  ACTIVE: ["Active", "success"],
  CLOSED: ["Closed", "secondary"],
  WITHDRAWN: ["Withdrawn", "secondary"],
  ELIGIBLE: ["Eligible", "success"],
  NOT_ELIGIBLE: ["Not eligible", "danger"],
  MANUAL_REVIEW: ["Manual review", "warning"],
  PENDING_PROVIDER: ["Pending provider response", "warning"],
  PASS: ["Pass", "success"],
  FAIL: ["Fail", "danger"],
  REVIEW: ["Review", "warning"],
  PENDING: ["Pending", "warning"],
  PENDING_REVIEW: ["Pending review", "info"],
  VERIFIED: ["Verified", "success"],
  RESUBMISSION_REQUIRED: ["Resubmit", "warning"],
  SUPERSEDED: ["Replaced", "secondary"],
  MISSING: ["Missing", "secondary"],
  PENDING_DISBURSEMENT: ["Awaiting disbursement", "info"],
  PROCESSING: ["Processing", "info"],
  SUCCEEDED: ["Succeeded", "success"],
  FAILED: ["Failed", "danger"],
  UNKNOWN: ["Unknown - reconcile", "warning"],
  SUCCESS: ["Success", "success"],
  DUE: ["Due", "info"],
  OVERDUE: ["Overdue", "danger"],
  PAID: ["Paid", "success"],
  AVAILABLE: ["Available", "success"]
};

export function statusLabel(status) {
  if (!status) return "-";
  return STATUS[status]?.[0] ?? String(status).replaceAll("_", " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

export function statusTone(status) {
  return STATUS[status]?.[1] ?? "secondary";
}

/** Customer-facing application timeline (section 8.10). */
export const APPLICATION_STEPS = ["Draft", "Submitted", "Eligibility", "Review", "Decision", "Disbursed"];

export function timelineIndex(status) {
  switch (status) {
    case "DRAFT": return 0;
    case "SUBMITTED": return 1;
    case "UNDER_REVIEW":
    case "RETURNED_FOR_CORRECTION": return 3;
    case "APPROVED":
    case "REJECTED":
    case "DISBURSEMENT_PENDING": return 4;
    case "ACTIVE":
    case "CLOSED": return 5;
    default: return 0;
  }
}

export const ROLE_LABELS = {
  CUSTOMER: "Customer",
  LOAN_OFFICER: "Loan officer",
  CREDIT_ANALYST: "Credit analyst",
  DOCUMENT_VERIFIER: "Document verifier",
  LOAN_APPROVER: "Loan approver",
  FINANCE_OFFICER: "Finance officer",
  ADMIN: "Administrator"
};

export const STAFF_ROLES = ["LOAN_OFFICER", "CREDIT_ANALYST", "DOCUMENT_VERIFIER", "LOAN_APPROVER", "FINANCE_OFFICER", "ADMIN"];

export function isStaff(user) {
  return Boolean(user?.roles?.some((r) => STAFF_ROLES.includes(r)));
}

export function homePathFor(user) {
  if (!user) return "/login";
  if (user.roles.includes("CUSTOMER")) return "/app/dashboard";
  if (user.roles.includes("ADMIN")) return "/admin/users";
  return "/staff/dashboard";
}
