import { useRef, useState } from "react";
import { PageHeader } from "../layouts/AppLayout";
import { AsyncContent, ConfirmDialog, EmptyState, InlineAlert, Pagination, StatusBadge } from "../components/common";
import { DisbursementSummary, LoanClosurePanel, TransactionStatusTimeline } from "../components/loan";
import { financeApi } from "../features/staff/staffApi";
import { useApiState } from "../hooks/useApiState";
import { formatDate, formatMoney, newIdempotencyKey } from "../utils/format";

/** Disbursement workbench (section 8.16). Simulated adapter only; idempotency key per attempt. */
export function DisbursementPage() {
  const state = useApiState(() => financeApi.eligible(), []);
  const [selected, setSelected] = useState(null);
  const [mode, setMode] = useState("SUCCESS");
  const [attempts, setAttempts] = useState([]);
  const [error, setError] = useState(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const key = useRef(null);

  const select = async (account) => {
    setSelected(account);
    setError(null);
    key.current = newIdempotencyKey(`disb-${account.loanAccountId}`);
    setAttempts(await financeApi.disbursements(account.loanAccountId).catch(() => []));
  };
  const fee = selected ? (Number(selected.principal) * Number(selected.processingFeePercent ?? 0)) / 100 : 0;

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      // Fee is computed server-side from the product's configured policy when omitted.
      const result = await financeApi.disburse(selected.loanAccountId,
        { grossAmount: selected.principal, simulatedOutcome: mode }, key.current);
      setAttempts((a) => [result, ...a.filter((x) => x.disbursementId !== result.disbursementId)]);
      if (result.status === "FAILED") key.current = newIdempotencyKey(`disb-${selected.loanAccountId}`);
      state.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
      setConfirmOpen(false);
    }
  };
  const reconcile = async (id) => {
    try {
      const r = await financeApi.reconcile(id);
      setAttempts((a) => a.map((x) => (x.disbursementId === id ? r : x)));
      state.reload();
    } catch (e) {
      setError(e);
    }
  };

  const latest = attempts[0];
  return (
    <>
      <PageHeader title="Disbursement workbench" subtitle="Approved loans awaiting funding (simulated transfers only)" />
      <div className="row g-3">
        <div className="col-lg-6">
          <AsyncContent state={state} empty={state.data?.length === 0} emptyTitle="No loans awaiting disbursement">
            <div className="card"><div className="table-responsive"><table className="table align-middle mb-0">
              <caption className="visually-hidden">Approved loans awaiting funding</caption>
              <thead><tr><th scope="col">Loan account</th><th scope="col">Approved amount</th><th scope="col">Approved</th>
                <th scope="col"><span className="visually-hidden">Open</span></th></tr></thead>
              <tbody>{state.data?.map((a) => (
                <tr key={a.loanAccountId}><td>{a.reference}</td><td>{formatMoney(a.principal)}</td><td>{formatDate(a.createdAt)}</td>
                  <td><button type="button" className="btn btn-sm btn-outline-primary" onClick={() => select(a)}>Open</button></td></tr>))}
              </tbody></table></div></div>
          </AsyncContent>
        </div>
        <div className="col-lg-6">
          {!selected ? <EmptyState title="Select a loan account" /> : (
            <section className="card"><div className="card-body">
              <h2 className="h6">{selected.reference} · {selected.productName}</h2>
              <DisbursementSummary gross={selected.principal} fee={fee} net={Number(selected.principal) - fee} status={latest?.status} />
              <p className="small text-body-secondary mt-2 mb-2">Idempotency key (read-only): <code>{key.current}</code></p>
              <InlineAlert error={error} />
              {latest && <TransactionStatusTimeline status={latest.status} />}
              {latest?.transactionRef && <p className="small">Transaction reference: {latest.transactionRef}</p>}
              {latest?.status === "UNKNOWN" ? (
                <InlineAlert tone="warning">Outcome unknown. Reconcile before any retry.
                  <button type="button" className="btn btn-sm btn-warning ms-2" onClick={() => reconcile(latest.disbursementId)}>Reconcile</button>
                </InlineAlert>
              ) : selected.status === "PENDING_DISBURSEMENT" && latest?.status !== "SUCCEEDED" && (
                <div className="d-flex flex-wrap gap-2 align-items-end">
                  <div><label htmlFor="simMode" className="form-label small mb-1">Simulated bank outcome</label>
                    <select id="simMode" className="form-select" value={mode} onChange={(e) => setMode(e.target.value)}>
                      <option value="SUCCESS">Success</option><option value="FAIL">Failure</option><option value="TIMEOUT">Timeout (unknown)</option>
                    </select></div>
                  <button type="button" className="btn btn-primary" onClick={() => setConfirmOpen(true)}>Submit simulated disbursement</button>
                </div>
              )}
              {attempts.length > 0 && (
                <ul className="small list-unstyled mt-3 mb-0">{attempts.map((a) => (
                  <li key={a.disbursementId}><StatusBadge status={a.status} /> {formatMoney(a.netAmount)} net · {a.failureReason ?? a.transactionRef ?? ""}</li>))}</ul>
              )}
            </div></section>
          )}
        </div>
      </div>
      <ConfirmDialog open={confirmOpen} title="Submit simulated disbursement?" busy={busy} onConfirm={submit}
        onCancel={() => setConfirmOpen(false)}>
        Gross {formatMoney(selected?.principal)}. The processing fee is deducted per product policy. No real money moves.
      </ConfirmDialog>
    </>
  );
}

/** Overdue monitoring and loan closure (section 8.19). */
export function OverduePage() {
  const [asOf, setAsOf] = useState("");
  const [page, setPage] = useState(0);
  const [accountId, setAccountId] = useState("");
  const [check, setCheck] = useState(null);
  const [error, setError] = useState(null);
  const [closeOpen, setCloseOpen] = useState(false);
  const [message, setMessage] = useState(null);
  const state = useApiState(() => financeApi.overdue(asOf, page), [asOf, page]);

  const loadCheck = async (id) => {
    setError(null);
    setMessage(null);
    try {
      setCheck(await financeApi.closureCheck(id.trim()));
    } catch (e) {
      setCheck(null);
      setError(e);
    }
  };
  const close = async () => {
    try {
      await financeApi.close(check.loanAccountId);
      setMessage("Loan closed.");
      setCheck(await financeApi.closureCheck(check.loanAccountId));
    } catch (e) {
      setError(e);
    } finally {
      setCloseOpen(false);
    }
  };

  return (
    <>
      <PageHeader title="Overdue monitoring" actions={
        <input type="date" className="form-control" aria-label="As-of date" value={asOf} onChange={(e) => { setAsOf(e.target.value); setPage(0); }} />} />
      <AsyncContent state={state} empty={state.data?.content.length === 0} emptyTitle="No overdue installments">
        <div className="card mb-3"><div className="table-responsive"><table className="table align-middle mb-0">
          <caption className="visually-hidden">Overdue installments</caption>
          <thead><tr><th scope="col">Account</th><th scope="col">Installment</th><th scope="col">Due date</th>
            <th scope="col">Days overdue</th><th scope="col">Outstanding</th><th scope="col">Status</th>
            <th scope="col"><span className="visually-hidden">Open</span></th></tr></thead>
          <tbody>{state.data?.content.map((r) => (
            <tr key={`${r.loanAccountId}-${r.installmentNo}`}>
              <td>LOAN-{r.loanAccountId.slice(0, 8).toUpperCase()}</td><td>{r.installmentNo}</td><td>{formatDate(r.dueDate)}</td>
              <td>{r.daysOverdue}</td><td>{formatMoney(r.outstanding)}</td><td><StatusBadge status={r.status} /></td>
              <td><button type="button" className="btn btn-sm btn-outline-primary" onClick={() => { setAccountId(r.loanAccountId); loadCheck(r.loanAccountId); }}>Open</button></td>
            </tr>))}</tbody></table></div></div>
        <Pagination page={page} totalPages={state.data?.totalPages} onChange={setPage} />
      </AsyncContent>
      <section className="card mb-3"><div className="card-body">
        <h2 className="h6">Closure check</h2>
        <form className="d-flex gap-2" onSubmit={(e) => { e.preventDefault(); loadCheck(accountId); }}>
          <input className="form-control" aria-label="Loan account id" placeholder="Loan account id" value={accountId}
            onChange={(e) => setAccountId(e.target.value)} />
          <button type="submit" className="btn btn-outline-primary">Check</button>
        </form>
      </div></section>
      {message && <InlineAlert tone="success">{message}</InlineAlert>}
      <InlineAlert error={error} />
      <LoanClosurePanel check={check} onClose={() => setCloseOpen(true)} />
      <ConfirmDialog open={closeOpen} title="Close this loan?" confirmTone="danger" confirmLabel="Close loan" onConfirm={close}
        onCancel={() => setCloseOpen(false)}>Closure is final and creates a closure confirmation for the customer.</ConfirmDialog>
    </>
  );
}
