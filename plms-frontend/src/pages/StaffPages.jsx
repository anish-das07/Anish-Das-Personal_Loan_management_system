import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { PageHeader } from "../layouts/AppLayout";
import { AsyncContent, ConfirmDialog, EmptyState, InlineAlert, Pagination, StatusBadge, SummaryCard } from "../components/common";
import { FormField, fieldProps } from "../components/forms";
import { ApprovalActionDialog, DocumentChecklist, EligibilityRuleList } from "../components/loan";
import { useApiState } from "../hooks/useApiState";
import { useAuth } from "../hooks/useAuth";
import { staffApi } from "../features/staff/staffApi";
import { documentApi } from "../features/applications/applicationApi";
import { formatDate, formatDateTime, formatMoney } from "../utils/format";
import { ROLE_LABELS, statusLabel } from "../utils/status";

const KPI_LABELS = {
  newApplications: "New", inReview: "In review", returned: "Returned", documentsPendingReview: "Documents to verify",
  awaitingDisbursement: "Awaiting disbursement", unknownDisbursements: "Disbursements to reconcile",
  pendingRepayments: "Payments to reconcile", overdueInstallments: "Overdue installments"
};

export function StaffDashboardPage() {
  const { user } = useAuth();
  const state = useApiState(() => staffApi.dashboard(), []);
  return (
    <>
      <PageHeader title="Staff dashboard" subtitle={user?.roles.map((r) => ROLE_LABELS[r]).join(", ")} />
      <AsyncContent state={state}>
        <div className="row g-3">
          {state.data && Object.entries(state.data).filter(([k]) => k !== "roles").map(([k, v]) => (
            <div key={k} className="col-6 col-lg-3"><SummaryCard label={KPI_LABELS[k] ?? k} value={v} /></div>
          ))}
        </div>
      </AsyncContent>
    </>
  );
}

/** Generic application work queue used by loan officers, credit analysts and approvers. */
export function ApplicationQueuePage({ title, defaultQueue = "REVIEW", queues = ["REVIEW", "NEW", "RETURNED", "DECIDED"] }) {
  const navigate = useNavigate();
  const [queue, setQueue] = useState(defaultQueue);
  const [page, setPage] = useState(0);
  const state = useApiState(() => staffApi.queue(queue, page), [queue, page]);
  return (
    <>
      <PageHeader title={title} actions={
        <select className="form-select" aria-label="Queue" value={queue} onChange={(e) => { setQueue(e.target.value); setPage(0); }}>
          {queues.map((q) => <option key={q} value={q}>{statusLabel(q)}</option>)}
        </select>} />
      <AsyncContent state={state} empty={state.data?.content.length === 0} emptyTitle="No applications in this queue">
        <div className="card"><div className="table-responsive">
          <table className="table align-middle mb-0">
            <caption className="visually-hidden">{title}</caption>
            <thead><tr><th scope="col">Application</th><th scope="col">Customer ref</th><th scope="col">Amount</th>
              <th scope="col">Product</th><th scope="col">Eligibility</th><th scope="col">Status</th><th scope="col">Submitted</th>
              <th scope="col"><span className="visually-hidden">Open</span></th></tr></thead>
            <tbody>
              {state.data?.content.map((r) => (
                <tr key={r.applicationId}>
                  <td>{r.reference}</td><td>{r.customerRef}</td><td>{formatMoney(r.requestedAmount)}</td><td>{r.productName}</td>
                  <td>{r.eligibilityOutcome ? <StatusBadge status={r.eligibilityOutcome} /> : "-"}</td>
                  <td><StatusBadge status={r.status} /></td><td>{formatDate(r.submittedAt)}</td>
                  <td><button type="button" className="btn btn-sm btn-outline-primary"
                    onClick={() => navigate(`/staff/applications/${r.applicationId}`)} aria-label={`Open ${r.reference}`}>Open</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div></div>
        <Pagination page={page} totalPages={state.data?.totalPages} onChange={setPage} />
      </AsyncContent>
    </>
  );
}

/**
 * Review / approval workspace (sections 8.14-8.15). Shows the evidence pack; actions depend on role,
 * and the server enforces role, state, maker-checker and the optimistic version.
 */
export function ApplicationReviewPage() {
  const { applicationId } = useParams();
  const { hasRole } = useAuth();
  const [dialog, setDialog] = useState(null);
  const [recommendation, setRecommendation] = useState({ recommendation: "APPROVE", reasonCode: "POLICY_MET", comment: "" });
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const state = useApiState(() => staffApi.review(applicationId), [applicationId]);

  const run = async (fn, success) => {
    setError(null);
    setMessage(null);
    try {
      await fn();
      setMessage(success);
      state.reload();
    } catch (e) {
      setError(e);
    }
  };
  const decide = async (payload) => {
    await staffApi.decide(applicationId, payload);
    setDialog(null);
    setMessage(`Decision recorded: ${statusLabel(payload.decision)}`);
    state.reload();
  };

  const d = state.data;
  const underReview = d?.application.status === "UNDER_REVIEW";
  return (
    <AsyncContent state={state}>
      {d && (
        <>
          <PageHeader title={`Review ${d.application.reference}`} actions={<StatusBadge status={d.application.status} />} />
          {message && <InlineAlert tone="success">{message}</InlineAlert>}
          <InlineAlert error={error} />
          <div className="row g-3">
            <div className="col-lg-6">
              <section className="card h-100"><div className="card-body">
                <h2 className="h6">Applicant</h2>
                <dl className="row small mb-0">
                  <dt className="col-5">Name</dt><dd className="col-7">{d.applicant.fullName}</dd>
                  <dt className="col-5">Employment</dt><dd className="col-7">{statusLabel(d.applicant.employmentType)} {d.applicant.employerName ? `· ${d.applicant.employerName}` : ""}</dd>
                  <dt className="col-5">Monthly income</dt><dd className="col-7">{formatMoney(d.application.monthlyIncome)}</dd>
                  <dt className="col-5">Existing obligations</dt><dd className="col-7">{formatMoney(d.application.existingMonthlyObligations)}</dd>
                  <dt className="col-5">Requested</dt><dd className="col-7">{formatMoney(d.application.requestedAmount)} · {d.application.requestedMonths} months</dd>
                  <dt className="col-5">Product</dt><dd className="col-7">{d.application.productName}</dd>
                  <dt className="col-5">Purpose</dt><dd className="col-7">{d.application.purpose}</dd>
                </dl>
              </div></section>
            </div>
            <div className="col-lg-6">
              <section className="card h-100"><div className="card-body">
                <h2 className="h6">Eligibility and credit</h2>
                {d.eligibility ? (
                  <>
                    <p className="small mb-1"><StatusBadge status={d.eligibility.outcome} /> policy {d.eligibility.policyVersion} ·
                      DTI {d.eligibility.debtToIncomeRatio ?? "-"} · est. EMI {formatMoney(d.eligibility.estimatedEmi)}</p>
                    <EligibilityRuleList rules={d.eligibility.rules} />
                  </>
                ) : <EmptyState title="Not assessed yet" />}
                {d.credit && <p className="small mb-0">Credit: <StatusBadge status={d.credit.status} /> band {statusLabel(d.credit.scoreBand)} · score {d.credit.score} · {d.credit.source}</p>}
                {hasRole("CREDIT_ANALYST", "LOAN_OFFICER") && underReview && (
                  <button type="button" className="btn btn-sm btn-outline-secondary mt-2"
                    onClick={() => run(() => staffApi.evaluate(applicationId), "Assessment re-run.")}>Re-run assessment</button>
                )}
              </div></section>
            </div>
            <div className="col-12">
              <section className="card"><div className="card-body">
                <h2 className="h6">Documents {d.missingMandatoryDocuments.length === 0 ? "· all mandatory verified" : `· missing: ${d.missingMandatoryDocuments.map(statusLabel).join(", ")}`}</h2>
                <DocumentChecklist items={d.documents} canUpload={false} />
              </div></section>
            </div>
            <div className="col-lg-6">
              <section className="card h-100"><div className="card-body">
                <h2 className="h6">Recommendations</h2>
                {d.recommendations.length === 0 ? <p className="small text-body-secondary">None yet.</p> : (
                  <ul className="small list-unstyled">{d.recommendations.map((r) => (
                    <li key={r.reviewId}>{formatDateTime(r.createdAt)} · {ROLE_LABELS[r.reviewerRole]} · <StatusBadge status={r.recommendation} label={statusLabel(r.recommendation)} /> {r.reasonCode} {r.comment}</li>))}</ul>
                )}
                {hasRole("LOAN_OFFICER", "CREDIT_ANALYST") && underReview && (
                  <form className="mt-2" onSubmit={(e) => { e.preventDefault(); run(() => staffApi.recommend(applicationId, recommendation), "Recommendation recorded."); }}>
                    <div className="row g-2">
                      <div className="col-sm-6"><FormField id="recommendation" label="Recommendation">
                        <select {...fieldProps("recommendation", null, { className: "form-select" })} value={recommendation.recommendation}
                          onChange={(e) => setRecommendation((r) => ({ ...r, recommendation: e.target.value }))}>
                          <option value="APPROVE">Recommend approval</option><option value="REJECT">Recommend rejection</option>
                          <option value="RETURN_FOR_CORRECTION">Request correction</option></select></FormField></div>
                      <div className="col-sm-6"><FormField id="recReason" label="Reason code">
                        <input {...fieldProps("recReason", null)} value={recommendation.reasonCode}
                          onChange={(e) => setRecommendation((r) => ({ ...r, reasonCode: e.target.value }))} required /></FormField></div>
                    </div>
                    <FormField id="recComment" label="Comment">
                      <input {...fieldProps("recComment", null)} value={recommendation.comment}
                        onChange={(e) => setRecommendation((r) => ({ ...r, comment: e.target.value }))} /></FormField>
                    <div className="d-flex gap-2">
                      <button type="submit" className="btn btn-sm btn-primary">Record recommendation</button>
                      {hasRole("LOAN_OFFICER") && <button type="button" className="btn btn-sm btn-outline-secondary"
                        onClick={() => run(() => staffApi.assign(applicationId), "Assigned to you.")}>Assign to me</button>}
                    </div>
                  </form>
                )}
              </div></section>
            </div>
            <div className="col-lg-6">
              <section className="card h-100"><div className="card-body">
                <h2 className="h6">Decision history</h2>
                {d.decisions.length === 0 ? <p className="small text-body-secondary">No decision yet.</p> : (
                  <ul className="small list-unstyled">{d.decisions.map((x) => (
                    <li key={x.decisionId}>{formatDateTime(x.decidedAt)} · {statusLabel(x.decision)} · {x.reasonCode}
                      {x.approvedAmount ? ` · ${formatMoney(x.approvedAmount)}` : ""}</li>))}</ul>
                )}
                {hasRole("LOAN_APPROVER") && underReview && (
                  <div className="d-flex flex-wrap gap-2">
                    <button type="button" className="btn btn-success" onClick={() => setDialog("APPROVE")}>Approve</button>
                    <button type="button" className="btn btn-outline-danger" onClick={() => setDialog("REJECT")}>Reject</button>
                    <button type="button" className="btn btn-outline-secondary" onClick={() => setDialog("RETURN_FOR_CORRECTION")}>Return for correction</button>
                  </div>
                )}
              </div></section>
            </div>
          </div>
          {dialog && <ApprovalActionDialog key={dialog} open decision={dialog} application={d.application}
            onConfirm={decide} onCancel={() => setDialog(null)} />}
        </>
      )}
    </AsyncContent>
  );
}

const DOC_REASONS = ["IMAGE_UNREADABLE", "DOCUMENT_EXPIRED", "NAME_MISMATCH", "INCOMPLETE_DOCUMENT", "WRONG_DOCUMENT_TYPE"];

/** Document verification queue with secure preview and reason-coded decisions (section 8.13). */
export function DocumentQueuePage() {
  const [status, setStatus] = useState("PENDING_REVIEW");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [reason, setReason] = useState("");
  const [comment, setComment] = useState("");
  const [error, setError] = useState(null);
  const [pendingDecision, setPendingDecision] = useState(null);
  const state = useApiState(() => documentApi.queue(status, page), [status, page]);

  const open = async (doc) => {
    setSelected(doc);
    setReason("");
    setComment("");
    setError(null);
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(null);
    try {
      setPreviewUrl(await documentApi.contentUrl(doc.documentId));
    } catch (e) {
      setError(e);
    }
  };
  const decide = async () => {
    setError(null);
    try {
      await documentApi.decide(selected.documentId, { decision: pendingDecision, reasonCode: reason || undefined, comment: comment || undefined });
      setSelected(null);
      setPendingDecision(null);
      state.reload();
    } catch (e) {
      setError(e);
      setPendingDecision(null);
    }
  };

  return (
    <>
      <PageHeader title="Document verification queue" actions={
        <select className="form-select" aria-label="Status filter" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          {["PENDING_REVIEW", "VERIFIED", "RESUBMISSION_REQUIRED", "REJECTED"].map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
        </select>} />
      <div className="row g-3">
        <div className="col-lg-6">
          <AsyncContent state={state} empty={state.data?.content.length === 0} emptyTitle="Queue is empty">
            <div className="card"><div className="table-responsive">
              <table className="table align-middle mb-0">
                <caption className="visually-hidden">Documents</caption>
                <thead><tr><th scope="col">Application</th><th scope="col">Category</th><th scope="col">Uploaded</th>
                  <th scope="col">Status</th><th scope="col"><span className="visually-hidden">Review</span></th></tr></thead>
                <tbody>{state.data?.content.map((doc) => (
                  <tr key={doc.documentId} className={selected?.documentId === doc.documentId ? "table-active" : ""}>
                    <td>APP-{doc.applicationId.slice(0, 8).toUpperCase()}</td><td>{statusLabel(doc.category)} v{doc.version}</td>
                    <td>{formatDate(doc.uploadedAt)}</td><td><StatusBadge status={doc.status} /></td>
                    <td><button type="button" className="btn btn-sm btn-outline-primary" onClick={() => open(doc)}>Review</button></td>
                  </tr>))}</tbody>
              </table>
            </div></div>
            <Pagination page={page} totalPages={state.data?.totalPages} onChange={setPage} />
          </AsyncContent>
        </div>
        <div className="col-lg-6">
          {selected ? (
            <section className="card"><div className="card-body">
              <h2 className="h6">{statusLabel(selected.category)} · {selected.fileName} · {Math.round(selected.sizeBytes / 1024)} KB</h2>
              <InlineAlert error={error} />
              {previewUrl && (selected.mimeType === "application/pdf"
                ? <iframe title="Document preview" src={previewUrl} className="doc-preview" />
                : <img alt={`${statusLabel(selected.category)} preview`} src={previewUrl} className="doc-preview" />)}
              {selected.status === "PENDING_REVIEW" && (
                <>
                  <FormField id="docReason" label="Reason code (required to reject or request resubmission)">
                    <select {...fieldProps("docReason", null, { className: "form-select" })} value={reason} onChange={(e) => setReason(e.target.value)}>
                      <option value="">Select…</option>{DOC_REASONS.map((r) => <option key={r} value={r}>{statusLabel(r)}</option>)}
                    </select></FormField>
                  <FormField id="docComment" label="Comment for the customer">
                    <input {...fieldProps("docComment", null)} value={comment} maxLength={500} onChange={(e) => setComment(e.target.value)} />
                  </FormField>
                  <div className="d-flex flex-wrap gap-2">
                    <button type="button" className="btn btn-success" onClick={() => setPendingDecision("VERIFIED")}>Verify</button>
                    <button type="button" className="btn btn-outline-danger" disabled={!reason} onClick={() => setPendingDecision("REJECTED")}>Reject</button>
                    <button type="button" className="btn btn-outline-warning" disabled={!reason}
                      onClick={() => setPendingDecision("RESUBMISSION_REQUIRED")}>Request resubmission</button>
                  </div>
                </>
              )}
            </div></section>
          ) : <EmptyState title="Select a document to review" />}
        </div>
      </div>
      <ConfirmDialog open={Boolean(pendingDecision)} title={`${statusLabel(pendingDecision)} this document?`}
        onConfirm={decide} onCancel={() => setPendingDecision(null)}>Decisions are recorded with your name and time.</ConfirmDialog>
    </>
  );
}

export function ReportsPage() {
  const state = useApiState(() => staffApi.portfolio(), []);
  const d = state.data;
  const table = (title, data) => (
    <div className="col-md-6 col-xl-4"><section className="card h-100"><div className="card-body">
      <h2 className="h6">{title}</h2>
      <table className="table table-sm mb-0"><tbody>{Object.entries(data).map(([k, v]) => (
        <tr key={k}><th scope="row" className="fw-normal">{statusLabel(k)}</th><td className="text-end">{v}</td></tr>))}</tbody></table>
    </div></section></div>
  );
  return (
    <>
      <PageHeader title="Reports" subtitle={d ? `Data as of ${formatDateTime(d.generatedAt)}` : undefined}
        actions={<Link to="/staff/overdue" className="btn btn-outline-primary">Overdue report</Link>} />
      <AsyncContent state={state}>
        {d && <div className="row g-3">
          {table("Applications", d.applicationsByStatus)}
          {table("Loan accounts", d.loanAccountsByStatus)}
          {table("Decisions", d.decisions)}
          {table("Disbursements", d.disbursementsByStatus)}
          {table("Repayments", d.repaymentsByStatus)}
        </div>}
      </AsyncContent>
    </>
  );
}
