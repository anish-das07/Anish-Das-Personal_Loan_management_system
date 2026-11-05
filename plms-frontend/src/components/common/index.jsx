import { useEffect, useId, useRef } from "react";
import { statusLabel, statusTone } from "../../utils/status";

/** Status badge: always shows text, colour is supplementary. */
export function StatusBadge({ status, label }) {
  return (
    <span className={`badge rounded-pill text-bg-${statusTone(status)}`} data-status={status}>
      {label ?? statusLabel(status)}
    </span>
  );
}

export function Spinner({ label = "Loading" }) {
  return (
    <div className="d-flex align-items-center gap-2 text-body-secondary py-3" role="status" aria-live="polite">
      <span className="spinner-border spinner-border-sm" aria-hidden="true" />
      <span>{label}…</span>
    </div>
  );
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="empty-state text-center py-5">
      <h3 className="h6 mb-1">{title}</h3>
      {children && <p className="text-body-secondary mb-3">{children}</p>}
      {action}
    </div>
  );
}

/** Inline alert. Errors show the correlation ID only as a support reference. */
export function InlineAlert({ tone = "danger", error, children, onRetry }) {
  if (!error && !children) return null;
  const message = children ?? error?.message ?? "Something went wrong.";
  return (
    <div className={`alert alert-${tone} d-flex flex-wrap align-items-center gap-2`} role={tone === "danger" ? "alert" : "status"}>
      <span className="me-auto">{message}</span>
      {error?.correlationId && <small className="text-body-secondary">Support reference: {error.correlationId}</small>}
      {onRetry && <button type="button" className="btn btn-sm btn-outline-secondary" onClick={onRetry}>Try again</button>}
    </div>
  );
}

/** Renders loading, error and empty states consistently around content. */
export function AsyncContent({ state, empty, emptyTitle = "Nothing here yet", children }) {
  if (state.loading && !state.data) return <Spinner />;
  if (state.error) return <InlineAlert error={state.error} onRetry={state.reload} />;
  if (empty) return <EmptyState title={emptyTitle} />;
  return children;
}

export function SummaryCard({ label, value, hint, status }) {
  return (
    <div className="card summary-card h-100" aria-label={`${label}: ${value}`}>
      <div className="card-body">
        <div className="text-body-secondary small">{label}</div>
        <div className="fs-4 fw-semibold mt-1">{value}</div>
        {status && <div className="mt-1"><StatusBadge status={status} /></div>}
        {hint && <div className="small text-body-secondary mt-1">{hint}</div>}
      </div>
    </div>
  );
}

/**
 * Accessible confirmation dialog: labelled, focus moves inside on open, Escape cancels,
 * focus is trapped while open and restored on close.
 */
export function ConfirmDialog({ open, title, children, confirmLabel = "Confirm", confirmTone = "primary",
  busy = false, confirmDisabled = false, onConfirm, onCancel }) {
  const titleId = useId();
  const ref = useRef(null);
  const cancelRef = useRef(onCancel);
  cancelRef.current = onCancel;
  useEffect(() => {
    if (!open) return undefined;
    const previous = document.activeElement;
    const node = ref.current;
    const focusables = () => Array.from(node.querySelectorAll("button, input, select, textarea, [tabindex]:not([tabindex='-1'])"))
      .filter((el) => !el.disabled);
    focusables()[0]?.focus();
    const onKey = (e) => {
      if (e.key === "Escape") cancelRef.current?.();
      if (e.key === "Tab") {
        const els = focusables();
        if (els.length === 0) return;
        const first = els[0];
        const last = els[els.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    node.addEventListener("keydown", onKey);
    return () => {
      node.removeEventListener("keydown", onKey);
      previous?.focus?.();
    };
  }, [open]);

  if (!open) return null;
  return (
    <div className="dialog-backdrop">
      <div ref={ref} className="card dialog-card shadow" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <div className="card-body">
          <h2 id={titleId} className="h5">{title}</h2>
          <div className="my-3">{children}</div>
          <div className="d-flex justify-content-end gap-2">
            <button type="button" className="btn btn-outline-secondary" onClick={onCancel} disabled={busy}>Cancel</button>
            <button type="button" className={`btn btn-${confirmTone}`} onClick={onConfirm} disabled={busy || confirmDisabled}>
              {busy ? "Working…" : confirmLabel}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav aria-label="Pagination" className="d-flex justify-content-end align-items-center gap-2 mt-3">
      <button type="button" className="btn btn-sm btn-outline-secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span className="small">Page {page + 1} of {totalPages}</span>
      <button type="button" className="btn btn-sm btn-outline-secondary" disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}>Next</button>
    </nav>
  );
}
