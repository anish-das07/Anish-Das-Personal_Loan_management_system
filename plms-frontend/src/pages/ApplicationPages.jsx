import { useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { PageHeader } from "../layouts/AppLayout";
import { AsyncContent, ConfirmDialog, InlineAlert, StatusBadge } from "../components/common";
import { FileUpload } from "../components/forms";
import { CreditScoreSummary, DOCUMENT_LABELS, DocumentChecklist, EligibilityResultCard, EligibilityRuleList,
  LoanApplicationTable, LoanStatusTimeline } from "../components/loan";
import { LoanApplicationWizard } from "../features/applications/LoanApplicationWizard";
import { applicationApi, customerApi, documentApi, productApi } from "../features/applications/applicationApi";
import { useApiState } from "../hooks/useApiState";
import { formatDate, formatDateTime, formatMoney, newIdempotencyKey } from "../utils/format";

export function ApplicationsListPage() {
  const navigate = useNavigate();
  const state = useApiState(() => applicationApi.mine(), []);
  return (
    <>
      <PageHeader title="My applications" actions={<Link className="btn btn-primary" to="/app/applications/new">New application</Link>} />
      <AsyncContent state={state}>
        <div className="card"><div className="card-body">
          <LoanApplicationTable rows={state.data} onOpen={(id) => navigate(`/app/applications/${id}`)} />
        </div></div>
      </AsyncContent>
    </>
  );
}

/** New draft or edit of a draft/returned application. */
export function ApplicationWizardPage() {
  const { applicationId } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const createKey = useRef(newIdempotencyKey("app-create"));
  const state = useApiState(async () => {
    const [products, profile, existing] = await Promise.all([
      productApi.list({ size: 100 }).then((p) => p.content), customerApi.me(),
      applicationId ? applicationApi.get(applicationId) : Promise.resolve(null)]);
    return { products, profile, existing };
  }, [applicationId]);

  const saveDraft = async (id, payload) => {
    if (id) {
      await applicationApi.update(id, payload);
      return id;
    }
    const created = await applicationApi.create(payload, createKey.current);
    return created.applicationId;
  };
  const submit = async (id) => {
    await applicationApi.submit(id, newIdempotencyKey("app-submit"));
    navigate(`/app/applications/${id}`, { replace: true, state: { submitted: true } });
  };

  const editable = !state.data?.existing || ["DRAFT", "RETURNED_FOR_CORRECTION"].includes(state.data.existing.status);
  return (
    <>
      <PageHeader title={applicationId ? "Edit application" : "New application"} />
      <AsyncContent state={state}>
        {state.data && (editable ? (
          <LoanApplicationWizard products={state.data.products} profile={state.data.profile}
            initial={state.data.existing ?? { productId: params.get("productId") ?? "" }} onSaveDraft={saveDraft} onSubmit={submit} />
        ) : <InlineAlert tone="warning">This application can no longer be edited.</InlineAlert>)}
      </AsyncContent>
    </>
  );
}

/** Status tracker with assessment, documents and history (sections 8.10-8.12). */
export function ApplicationStatusPage() {
  const { applicationId } = useParams();
  const [uploadCategory, setUploadCategory] = useState(null);
  const [file, setFile] = useState(null);
  const [progress, setProgress] = useState(0);
  const [uploadError, setUploadError] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const state = useApiState(async () => {
    const app = await applicationApi.get(applicationId);
    const [history, checklist, eligibility, credit] = await Promise.all([
      applicationApi.history(applicationId),
      documentApi.checklist(applicationId),
      app.status === "DRAFT" ? null : applicationApi.eligibility(applicationId).catch(() => null),
      app.status === "DRAFT" ? [] : applicationApi.credit(applicationId).catch(() => [])
    ]);
    return { app, history, checklist, eligibility, credit: credit[0] ?? null };
  }, [applicationId]);

  const upload = async () => {
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    try {
      await documentApi.upload(applicationId, uploadCategory, file,
        (e) => setProgress(e.total ? Math.round((e.loaded / e.total) * 100) : 0));
      setUploadCategory(null);
      setFile(null);
      state.reload();
    } catch (e) {
      setUploadError(e);
    } finally {
      setUploading(false);
      setProgress(0);
    }
  };
  const withdraw = async () => {
    await applicationApi.withdraw(applicationId).catch((e) => setUploadError(e));
    setWithdrawOpen(false);
    state.reload();
  };

  const d = state.data;
  const canUpload = d && ["DRAFT", "SUBMITTED", "UNDER_REVIEW", "RETURNED_FOR_CORRECTION"].includes(d.app.status);
  return (
    <AsyncContent state={state}>
      {d && (
        <>
          <PageHeader title={`Application ${d.app.reference}`} subtitle={`Submitted ${formatDate(d.app.submittedAt)}`}
            actions={<>
              <StatusBadge status={d.app.status} />
              {["DRAFT", "RETURNED_FOR_CORRECTION"].includes(d.app.status) &&
                <Link className="btn btn-primary btn-sm" to={`/app/applications/${applicationId}/edit`}>Continue editing</Link>}
              {["DRAFT", "SUBMITTED", "RETURNED_FOR_CORRECTION"].includes(d.app.status) &&
                <button type="button" className="btn btn-outline-danger btn-sm" onClick={() => setWithdrawOpen(true)}>Withdraw</button>}
            </>} />
          <section className="card mb-3"><div className="card-body">
            <LoanStatusTimeline status={d.app.status} />
            <div className="row small mt-2">
              <div className="col-sm-4">Requested amount: <strong>{formatMoney(d.app.requestedAmount)}</strong></div>
              <div className="col-sm-4">Product: <strong>{d.app.productName}</strong></div>
              <div className="col-sm-4">Tenure: <strong>{d.app.requestedMonths} months</strong></div>
            </div>
            <p className="mb-0 mt-2"><strong>Next step:</strong> {d.app.nextAction}</p>
          </div></section>

          {d.app.status !== "DRAFT" && (
            <section className="mb-3" aria-labelledby="assessment-heading">
              <h2 id="assessment-heading" className="h5">Assessment summary</h2>
              <div className="row g-3 mb-2">
                <div className="col-md-6"><EligibilityResultCard assessment={d.eligibility} /></div>
                <div className="col-md-6"><CreditScoreSummary assessment={d.credit} /></div>
              </div>
              {d.eligibility?.outcome === "MANUAL_REVIEW" && <InlineAlert tone="info">A specialist review is required. We will update you.</InlineAlert>}
              <EligibilityRuleList rules={d.eligibility?.rules} />
            </section>
          )}

          <section className="card mb-3" aria-labelledby="docs-heading"><div className="card-body">
            <h2 id="docs-heading" className="h5">Required documents</h2>
            <DocumentChecklist items={d.checklist} canUpload={canUpload} onUpload={setUploadCategory} />
            {uploadCategory && (
              <div className="border rounded p-3 mt-2">
                <p className="fw-semibold mb-2">Upload {DOCUMENT_LABELS[uploadCategory]}</p>
                <FileUpload id="documentFile" onSelect={setFile} disabled={uploading} />
                <p className="small text-body-secondary mt-1 mb-2">PDF, JPG or PNG up to 5 MB. No password-protected files. Files are stored privately.</p>
                {uploading && <div className="progress mb-2" role="progressbar" aria-label="Upload progress" aria-valuenow={progress}
                  aria-valuemin="0" aria-valuemax="100"><div className="progress-bar" style={{ width: `${progress}%` }} /></div>}
                <InlineAlert error={uploadError} />
                <div className="d-flex gap-2">
                  <button type="button" className="btn btn-primary btn-sm" disabled={!file || uploading} onClick={upload}>Upload</button>
                  <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setUploadCategory(null)}>Cancel</button>
                </div>
              </div>
            )}
          </div></section>

          <section className="card" aria-labelledby="history-heading"><div className="card-body">
            <h2 id="history-heading" className="h5">History</h2>
            <ul className="list-unstyled mb-0">
              {d.history.map((h, i) => (
                <li key={i} className="border-bottom py-1 small">
                  <span className="text-body-secondary">{formatDateTime(h.occurredAt)}</span> · <StatusBadge status={h.toStatus} /> {h.explanation}
                </li>
              ))}
            </ul>
          </div></section>
          <ConfirmDialog open={withdrawOpen} title="Withdraw application?" confirmLabel="Withdraw" confirmTone="danger"
            onConfirm={withdraw} onCancel={() => setWithdrawOpen(false)}>Withdrawn applications cannot be reopened.</ConfirmDialog>
        </>
      )}
    </AsyncContent>
  );
}
