import { useState } from "react";
import { Link } from "react-router-dom";
import { APPLICATION_STEPS, statusLabel, timelineIndex } from "../../utils/status";
import { formatDate, formatDateTime, formatMoney, normaliseMoneyInput, shortRef } from "../../utils/format";
import { ConfirmDialog, EmptyState, StatusBadge } from "../common";
import { CurrencyInput, FormField, fieldProps, isValidMoney } from "../forms";

/** Application timeline; vertical on narrow screens via CSS. */
export function LoanStatusTimeline({ status, steps = APPLICATION_STEPS }) {
  const current = timelineIndex(status);
  const terminalNegative = status === "REJECTED" || status === "WITHDRAWN";
  return (
    <ol className="status-timeline" aria-label="Application status timeline">
      {steps.map((step, i) => (
        <li key={step} className={i < current ? "done" : i === current ? (terminalNegative ? "stopped" : "current") : ""}
          aria-current={i === current ? "step" : undefined}>
          <span className="dot" aria-hidden="true" />
          <span>{step}</span>
        </li>
      ))}
    </ol>
  );
}

/** Customer's applications. Rows are only what the API returned; visibility is not authorisation. */
export function LoanApplicationTable({ rows, onOpen }) {
  if (!rows?.length) return <EmptyState title="No applications yet">Start by choosing a loan product.</EmptyState>;
  return (
    <div className="table-responsive">
      <table className="table align-middle">
        <caption className="visually-hidden">My applications</caption>
        <thead><tr><th scope="col">Reference</th><th scope="col">Product</th><th scope="col">Amount</th>
          <th scope="col">Status</th><th scope="col">Updated</th><th scope="col"><span className="visually-hidden">Actions</span></th></tr></thead>
        <tbody>
          {rows.map((a) => (
            <tr key={a.applicationId}>
              <td>{a.reference}</td>
              <td>{a.productName}</td>
              <td>{formatMoney(a.requestedAmount)}</td>
              <td><StatusBadge status={a.status} /></td>
              <td>{formatDate(a.updatedAt)}</td>
              <td className="text-end">
                <button type="button" className="btn btn-sm btn-outline-primary" onClick={() => onOpen(a.applicationId)}
                  aria-label={`View ${a.reference}`}>View</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function AmortizationScheduleTable({ rows }) {
  if (!rows?.length) return <EmptyState title="Schedule not generated yet" />;
  return (
    <div className="table-responsive schedule-table">
      <table className="table table-sm align-middle">
        <caption className="visually-hidden">Amortisation schedule</caption>
        <thead><tr><th scope="col">#</th><th scope="col">Due date</th><th scope="col">Opening balance</th>
          <th scope="col">Principal</th><th scope="col">Interest</th><th scope="col">EMI</th><th scope="col">Closing balance</th>
          {rows[0].status !== undefined && <th scope="col">Status</th>}</tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.installmentNo}>
              <td>{r.installmentNo}</td>
              <td>{formatDate(r.dueDate)}</td>
              <td>{formatMoney(r.openingBalance)}</td>
              <td>{formatMoney(r.principal)}</td>
              <td>{formatMoney(r.interest)}</td>
              <td>{formatMoney(r.total)}</td>
              <td>{formatMoney(r.closingBalance)}</td>
              {r.status !== undefined && <td><StatusBadge status={r.status} /></td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Neutral, accurate language: eligibility is an assessment, not an approval. */
export function EligibilityResultCard({ assessment }) {
  if (!assessment) return <EmptyState title="Assessment not available yet" />;
  return (
    <div className="card h-100">
      <div className="card-body">
        <h3 className="h6">Eligibility result</h3>
        <p className="mb-2"><StatusBadge status={assessment.outcome} /></p>
        {"passed" in assessment && (
          <p className="small mb-1">{assessment.passed} passed · {assessment.needsReview} need review · {assessment.pending} pending
            {assessment.failed ? ` · ${assessment.failed} not met` : ""}</p>
        )}
        <p className="small text-body-secondary mb-0">Policy {assessment.policyVersion} · {formatDateTime(assessment.evaluatedAt)}</p>
        <p className="small text-body-secondary mb-0 mt-2">Eligibility is an assessment, not a loan approval.</p>
      </div>
    </div>
  );
}

export function EligibilityRuleList({ rules }) {
  if (!rules?.length) return null;
  return (
    <div className="table-responsive">
      <table className="table table-sm">
        <caption className="visually-hidden">Eligibility rule results</caption>
        <thead><tr><th scope="col">Rule</th><th scope="col">Outcome</th><th scope="col">Explanation</th></tr></thead>
        <tbody>
          {rules.map((r) => (
            <tr key={r.code}>
              <td>{statusLabel(r.code)}</td>
              <td><StatusBadge status={r.result} /></td>
              <td>{r.reason}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Customer views may omit the raw score; the API decides what is included. */
export function CreditScoreSummary({ assessment }) {
  return (
    <div className="card h-100">
      <div className="card-body">
        <h3 className="h6">Credit assessment</h3>
        {!assessment ? <p className="mb-0 text-body-secondary">Not requested yet</p> : (
          <>
            <p className="mb-1"><StatusBadge status={assessment.status} /></p>
            {assessment.scoreBand && <p className="mb-1 small">Band: {statusLabel(assessment.scoreBand)}</p>}
            {assessment.score != null && <p className="mb-1 small">Synthetic score: {assessment.score}</p>}
            {assessment.status === "PENDING_PROVIDER" && (
              <p className="small mb-1">The provider did not respond. This is pending, not a failed score.</p>)}
            <p className="small text-body-secondary mb-0">{assessment.source} · {formatDateTime(assessment.assessedAt)}</p>
          </>
        )}
      </div>
    </div>
  );
}

export const DOCUMENT_LABELS = {
  IDENTITY_PROOF: "Identity proof",
  INCOME_PROOF: "Income proof",
  BANK_STATEMENT: "Bank statement",
  ADDRESS_PROOF: "Address proof"
};

export function DocumentChecklist({ items, onUpload, canUpload }) {
  if (!items?.length) return <EmptyState title="No document requirements" />;
  return (
    <div className="table-responsive">
      <table className="table align-middle">
        <caption className="visually-hidden">Required documents</caption>
        <thead><tr><th scope="col">Document</th><th scope="col">Accepted types</th><th scope="col">Status</th>
          <th scope="col"><span className="visually-hidden">Action</span></th></tr></thead>
        <tbody>
          {items.map((d) => (
            <tr key={d.category}>
              <td>
                {DOCUMENT_LABELS[d.category] ?? d.category}{d.mandatory ? " *" : ""}
                {d.reviewerComment && <div className="small text-warning-emphasis">Reviewer: {d.reviewerComment}</div>}
              </td>
              <td className="small">PDF, JPG, PNG</td>
              <td><StatusBadge status={d.status} />{d.version ? <span className="small text-body-secondary"> v{d.version}</span> : null}</td>
              <td className="text-end">
                {canUpload && d.status !== "VERIFIED" && (
                  <button type="button" className="btn btn-sm btn-outline-primary" onClick={() => onUpload(d.category)}>
                    {d.status === "MISSING" ? "Upload" : "Replace"}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const REASON_CODES = {
  APPROVE: ["POLICY_MET", "POLICY_MET_WITH_REDUCED_AMOUNT"],
  REJECT: ["POLICY_NOT_MET", "AFFORDABILITY_CONCERN", "DOCUMENTS_UNSATISFACTORY"],
  RETURN_FOR_CORRECTION: ["INFORMATION_MISSING", "DOCUMENT_RESUBMISSION"]
};

/**
 * Decision dialog: reason code required, amount/tenure for approvals, optimistic version sent so the
 * server can reject a stale decision. Focus handling comes from ConfirmDialog.
 */
export function ApprovalActionDialog({ open, decision, application, onConfirm, onCancel }) {
  const [reasonCode, setReasonCode] = useState("");
  const [amount, setAmount] = useState(application?.requestedAmount != null ? String(application.requestedAmount) : "");
  const [months, setMonths] = useState(application?.requestedMonths ?? "");
  const [comment, setComment] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const approving = decision === "APPROVE";

  const confirm = async () => {
    if (!reasonCode) { setError("Select a reason code."); return; }
    if (approving && !isValidMoney(amount)) { setError("Enter a valid approved amount."); return; }
    setBusy(true);
    setError(null);
    try {
      await onConfirm({
        decision, reasonCode, comment: comment || undefined, expectedVersion: application?.version,
        ...(approving ? { approvedAmount: Number(normaliseMoneyInput(amount)), approvedMonths: Number(months) } : {})
      });
    } catch (e) {
      setError(e?.message || "The decision could not be saved.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <ConfirmDialog open={open} title={`${statusLabel(decision)} application`} confirmLabel="Confirm decision"
      confirmTone={decision === "REJECT" ? "danger" : "primary"} busy={busy} onConfirm={confirm} onCancel={onCancel}>
      {error && <div className="alert alert-danger py-2" role="alert">{error}</div>}
      <FormField id="reasonCode" label="Reason code" required>
        <select {...fieldProps("reasonCode", null, { className: "form-select" })} value={reasonCode}
          onChange={(e) => setReasonCode(e.target.value)}>
          <option value="">Select…</option>
          {(REASON_CODES[decision] || []).map((r) => <option key={r} value={r}>{statusLabel(r)}</option>)}
        </select>
      </FormField>
      {approving && (
        <div className="row g-2">
          <div className="col-sm-7">
            <FormField id="approvedAmount" label="Approved amount" required>
              <CurrencyInput id="approvedAmount" value={amount} onChange={setAmount} />
            </FormField>
          </div>
          <div className="col-sm-5">
            <FormField id="approvedMonths" label="Tenure (months)" required>
              <input {...fieldProps("approvedMonths", null)} type="number" min="1" value={months}
                onChange={(e) => setMonths(e.target.value)} />
            </FormField>
          </div>
        </div>
      )}
      <FormField id="decisionComment" label="Comment">
        <textarea {...fieldProps("decisionComment", null)} rows={2} maxLength={500} value={comment}
          onChange={(e) => setComment(e.target.value)} />
      </FormField>
    </ConfirmDialog>
  );
}

export function DisbursementSummary({ gross, fee, net, status }) {
  return (
    <dl className="row mb-0 money-summary">
      <dt className="col-7">Gross amount</dt><dd className="col-5 text-end">{formatMoney(gross)}</dd>
      <dt className="col-7">Processing fee</dt><dd className="col-5 text-end">{formatMoney(fee)}</dd>
      <dt className="col-7">Net simulated amount</dt><dd className="col-5 text-end fw-semibold">{formatMoney(net)}</dd>
      {status && (<><dt className="col-7">Status</dt><dd className="col-5 text-end"><StatusBadge status={status} /></dd></>)}
    </dl>
  );
}

/** Distinguishes success, failure and unknown outcomes. */
export function TransactionStatusTimeline({ status }) {
  const steps = ["Requested", "Processing", status === "FAILED" ? "Failed" : status === "UNKNOWN" ? "Unknown" : "Succeeded"];
  const reached = status === "PROCESSING" ? 1 : status ? 2 : 0;
  return (
    <ol className="status-timeline compact" aria-label="Transaction status">
      {steps.map((s, i) => (
        <li key={s} className={i < reached ? "done" : i === reached ? (status === "FAILED" ? "stopped" : "current") : ""}
          aria-current={i === reached ? "step" : undefined}><span className="dot" aria-hidden="true" />{s}</li>
      ))}
    </ol>
  );
}

export function RepaymentReceipt({ receipt }) {
  if (!receipt) return null;
  return (
    <div className="card border-success" aria-live="polite">
      <div className="card-body">
        <h3 className="h6">Receipt {receipt.receiptNo}</h3>
        <dl className="row small mb-0">
          <dt className="col-6">Status</dt><dd className="col-6"><StatusBadge status={receipt.status} /></dd>
          <dt className="col-6">Reference</dt><dd className="col-6">{receipt.reference}</dd>
          <dt className="col-6">Installment</dt><dd className="col-6">{receipt.installmentNo}</dd>
          <dt className="col-6">Amount</dt><dd className="col-6">{formatMoney(receipt.amount)}</dd>
          <dt className="col-6">Interest</dt><dd className="col-6">{formatMoney(receipt.interestAllocated)}</dd>
          <dt className="col-6">Principal</dt><dd className="col-6">{formatMoney(receipt.principalAllocated)}</dd>
          <dt className="col-6">Paid at</dt><dd className="col-6">{formatDateTime(receipt.paidAt)}</dd>
        </dl>
        <p className="small text-body-secondary mb-0 mt-2">{receipt.note}</p>
      </div>
    </div>
  );
}

export function LoanClosurePanel({ check, onClose, busy }) {
  if (!check) return null;
  return (
    <div className="card">
      <div className="card-body">
        <h3 className="h6">Loan closure · {shortRef("LOAN", check.loanAccountId)}</h3>
        {check.blockers.length > 0 ? (
          <ul className="small mb-2" aria-label="Closure blockers">{check.blockers.map((b) => <li key={b}>{b}</li>)}</ul>
        ) : <p className="small mb-2">All dues are settled and there are no unresolved transactions.</p>}
        <button type="button" className="btn btn-outline-danger btn-sm" disabled={!check.closable || busy} onClick={onClose}>
          Request closure
        </button>
      </div>
    </div>
  );
}

export function EMIDueCard({ account, nextInstallment }) {
  return (
    <div className="card h-100">
      <div className="card-body">
        <h3 className="h6">Current loan</h3>
        {!account ? <p className="text-body-secondary mb-0">No active loan.</p> : (
          <>
            <p className="mb-1">Outstanding: <strong>{formatMoney(account.outstandingPrincipal)}</strong></p>
            <p className="mb-1">EMI: {formatMoney(account.monthlyEmi)}</p>
            {nextInstallment && <p className="mb-2">Next due: {formatDate(nextInstallment.dueDate)}</p>}
            <Link to={`/app/loans/${account.loanAccountId}`} className="btn btn-sm btn-outline-primary">View schedule</Link>
          </>
        )}
      </div>
    </div>
  );
}
