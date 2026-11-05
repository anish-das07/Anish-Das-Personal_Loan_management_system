import { useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PageHeader } from "../layouts/AppLayout";
import { AsyncContent, ConfirmDialog, EmptyState, InlineAlert, StatusBadge, SummaryCard } from "../components/common";
import { AmortizationScheduleTable, RepaymentReceipt } from "../components/loan";
import { accountApi } from "../features/staff/staffApi";
import { useApiState } from "../hooks/useApiState";
import { formatDate, formatDateTime, formatMoney, newIdempotencyKey } from "../utils/format";

export function LoanAccountsPage() {
  const state = useApiState(() => accountApi.mine(), []);
  return (
    <>
      <PageHeader title="Loan accounts" />
      <AsyncContent state={state} empty={state.data?.length === 0} emptyTitle="No loan accounts yet">
        <div className="row g-3">
          {state.data?.map((a) => (
            <div key={a.loanAccountId} className="col-md-6">
              <article className="card h-100"><div className="card-body">
                <h2 className="h6 d-flex justify-content-between">{a.reference} <StatusBadge status={a.status} /></h2>
                <p className="small mb-1">{a.productName} · {formatMoney(a.principal)} · {a.tenureMonths} months at {a.annualInterestRate}%</p>
                <p className="small mb-2">Outstanding principal: <strong>{formatMoney(a.outstandingPrincipal)}</strong></p>
                <Link to={`/app/loans/${a.loanAccountId}`} className="btn btn-sm btn-outline-primary">View schedule and pay</Link>
              </div></article>
            </div>
          ))}
        </div>
      </AsyncContent>
    </>
  );
}

/** Schedule, repayments and simulated EMI payment with a traceable receipt (sections 8.17-8.18). */
export function LoanAccountPage() {
  const { accountId } = useParams();
  const [method, setMethod] = useState("SIMULATED");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [receipt, setReceipt] = useState(null);
  const [error, setError] = useState(null);
  // One idempotency key per logical payment attempt: a retry of the same attempt reuses it.
  const paymentKey = useRef(null);

  const state = useApiState(async () => {
    const [account, schedule, repayments] = await Promise.all([accountApi.get(accountId), accountApi.schedule(accountId),
      accountApi.repayments(accountId)]);
    return { account, schedule, repayments, next: schedule.installments.find((i) => i.status !== "PAID") ?? null };
  }, [accountId]);

  const pay = async () => {
    const next = state.data.next;
    if (!paymentKey.current) paymentKey.current = newIdempotencyKey(`repay-${accountId}-${next.installmentNo}`);
    setBusy(true);
    setError(null);
    try {
      const r = await accountApi.pay({ loanAccountId: accountId, installmentNo: next.installmentNo, amount: next.total,
        paymentMethod: method }, paymentKey.current);
      setResult(r);
      paymentKey.current = null;
      if (r.status === "SUCCESS") setReceipt(await accountApi.receipt(r.repaymentId));
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
      const r = await accountApi.reconcile(id);
      setResult(r);
      if (r.status === "SUCCESS") setReceipt(await accountApi.receipt(id));
      state.reload();
    } catch (e) {
      setError(e);
    }
  };

  const d = state.data;
  const pending = d?.repayments.find((r) => r.status === "PENDING");
  return (
    <AsyncContent state={state}>
      {d && (
        <>
          <PageHeader title={`Loan ${d.account.reference}`} subtitle={`${d.account.productName} · ${d.account.annualInterestRate}% p.a.`}
            actions={<StatusBadge status={d.account.status} />} />
          <div className="row g-3 mb-3">
            <div className="col-6 col-lg-3"><SummaryCard label="Principal" value={formatMoney(d.account.principal)} /></div>
            <div className="col-6 col-lg-3"><SummaryCard label="Monthly EMI" value={formatMoney(d.schedule.monthlyEmi)} /></div>
            <div className="col-6 col-lg-3"><SummaryCard label="Outstanding principal" value={formatMoney(d.account.outstandingPrincipal)} /></div>
            <div className="col-6 col-lg-3"><SummaryCard label="Total interest" value={formatMoney(d.schedule.totalInterest)} /></div>
          </div>

          {d.account.status === "ACTIVE" && d.next && (
            <section className="card mb-3" aria-labelledby="pay-heading"><div className="card-body">
              <h2 id="pay-heading" className="h5">Pay EMI</h2>
              <p className="mb-2">Installment {d.next.installmentNo} · due {formatDate(d.next.dueDate)} ·
                principal {formatMoney(d.next.principal)} · interest {formatMoney(d.next.interest)} · <strong>due {formatMoney(d.next.total)}</strong></p>
              {pending ? (
                <InlineAlert tone="warning">
                  A payment is awaiting confirmation. <button type="button" className="btn btn-link p-0 align-baseline"
                    onClick={() => reconcile(pending.repaymentId)}>Check status</button>
                </InlineAlert>
              ) : (
                <div className="d-flex flex-wrap gap-2 align-items-end">
                  <div>
                    <label htmlFor="paymentMethod" className="form-label small mb-1">Payment mode (simulated)</label>
                    <select id="paymentMethod" className="form-select" value={method} onChange={(e) => setMethod(e.target.value)}>
                      <option value="SIMULATED">Simulated - success</option>
                      <option value="SIMULATED_DECLINE">Simulated - declined</option>
                      <option value="SIMULATED_TIMEOUT">Simulated - timeout (unknown)</option>
                    </select>
                  </div>
                  <button type="button" className="btn btn-primary" onClick={() => setConfirmOpen(true)}>Confirm simulated payment</button>
                </div>
              )}
              <div aria-live="polite" className="mt-2">
                {result && result.status === "FAILED" && <InlineAlert>Payment failed ({result.failureReason}). Your installment is still due.</InlineAlert>}
                {result && result.status === "PENDING" && <InlineAlert tone="warning">The payment outcome is unknown. It will be reconciled; do not pay again.</InlineAlert>}
                <InlineAlert error={error} />
              </div>
            </div></section>
          )}
          {receipt && <div className="mb-3"><RepaymentReceipt receipt={receipt} /></div>}

          <section className="card mb-3"><div className="card-body">
            <h2 className="h5">Amortisation schedule</h2>
            <AmortizationScheduleTable rows={d.schedule.installments} />
            <p className="small text-body-secondary mb-0">Rounding: {d.schedule.roundingPolicy}.</p>
          </div></section>

          <section className="card"><div className="card-body">
            <h2 className="h5">Repayments</h2>
            {d.repayments.length === 0 ? <EmptyState title="No repayments yet" /> : (
              <ul className="list-unstyled mb-0">
                {d.repayments.map((r) => (
                  <li key={r.repaymentId} className="border-bottom py-1 small d-flex flex-wrap gap-2 align-items-center">
                    <span>#{r.installmentNo}</span><span>{formatMoney(r.amount)}</span><StatusBadge status={r.status} />
                    <span className="text-body-secondary">{r.reference ?? ""} · {formatDateTime(r.createdAt)}</span>
                    {r.status === "SUCCESS" && <button type="button" className="btn btn-link btn-sm p-0"
                      onClick={async () => setReceipt(await accountApi.receipt(r.repaymentId))}>View receipt</button>}
                  </li>
                ))}
              </ul>
            )}
          </div></section>
          <ConfirmDialog open={confirmOpen} title="Confirm simulated payment" confirmLabel="Pay" busy={busy}
            onConfirm={pay} onCancel={() => setConfirmOpen(false)}>
            Pay {formatMoney(d.next?.total)} for installment {d.next?.installmentNo} using a simulated payment method. No real money moves.
          </ConfirmDialog>
        </>
      )}
    </AsyncContent>
  );
}
