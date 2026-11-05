import { useId, useState } from "react";
import { normaliseMoneyInput } from "../../utils/format";

/**
 * Labelled form field (case study section 9): associates the label, marks required fields and
 * links the error message through aria-describedby on the child control.
 */
export function FormField({ id, label, error, required, hint, children }) {
  const errorId = error ? `${id}-error` : undefined;
  const hintId = hint ? `${id}-hint` : undefined;
  return (
    <div className="form-field mb-3">
      <label htmlFor={id} className="form-label">
        {label}{required ? " *" : ""}
      </label>
      {children}
      {hint && <div id={hintId} className="form-text">{hint}</div>}
      {error && <p id={errorId} role="alert" className="invalid-feedback d-block mb-0">{error}</p>}
    </div>
  );
}

/** Props for a text input inside FormField, keeping aria wiring consistent. */
export function fieldProps(id, error, extra = {}) {
  return {
    id,
    name: id,
    className: `form-control${error ? " is-invalid" : ""}`,
    "aria-invalid": Boolean(error),
    "aria-describedby": error ? `${id}-error` : undefined,
    ...extra
  };
}

/**
 * Money input. Keeps the value as a string (no floating-point maths in the client) and reports
 * whether it is a valid amount; the currency is announced to assistive technology.
 */
export function CurrencyInput({ id, value, onChange, error, currency = "INR", placeholder = "0.00", disabled }) {
  return (
    <div className="input-group">
      <span className="input-group-text" aria-hidden="true">₹</span>
      <input {...fieldProps(id, error)} inputMode="decimal" value={value} placeholder={placeholder} disabled={disabled}
        onChange={(e) => onChange(e.target.value)} />
      <span className="visually-hidden">Amount in {currency}</span>
    </div>
  );
}

export function isValidMoney(value, { allowZero = false } = {}) {
  const n = normaliseMoneyInput(value);
  return n !== null && (allowZero || Number(n) > 0);
}

/**
 * File picker with client-side hints for type and size. These checks only improve feedback;
 * the server validates content, size and authorisation again.
 */
export function FileUpload({ id, accept = ".pdf,.jpg,.jpeg,.png", maxBytes = 5 * 1024 * 1024, onSelect, disabled }) {
  const [hint, setHint] = useState(null);
  const allowed = accept.split(",").map((a) => a.trim().toLowerCase());
  const handle = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const ext = "." + (file.name.split(".").pop() || "").toLowerCase();
    if (!allowed.includes(ext)) {
      setHint(`This file type is not accepted. Use ${allowed.join(", ").toUpperCase()}.`);
      e.target.value = "";
      return;
    }
    if (file.size > maxBytes) {
      setHint(`The file is larger than ${Math.round(maxBytes / 1024 / 1024)} MB.`);
      e.target.value = "";
      return;
    }
    setHint(null);
    onSelect(file);
  };
  return (
    <div>
      <input id={id} type="file" className="form-control" accept={accept} onChange={handle} disabled={disabled}
        aria-describedby={hint ? `${id}-hint` : undefined} />
      {hint && <p id={`${id}-hint`} role="alert" className="text-danger small mb-0 mt-1">{hint}</p>}
    </div>
  );
}

/** Wizard progress indicator; the current step is exposed with aria-current. */
export function StepIndicator({ steps, currentStep }) {
  return (
    <ol className="step-indicator" aria-label="Application progress">
      {steps.map((step, i) => (
        <li key={step} className={i === currentStep ? "current" : i < currentStep ? "done" : ""}
          aria-current={i === currentStep ? "step" : undefined}>
          <span className="step-number">{i + 1}</span> <span className="step-label">{step}</span>
        </li>
      ))}
    </ol>
  );
}

export function ValidationSummary({ errors }) {
  const id = useId();
  const entries = Object.entries(errors || {}).filter(([, v]) => v);
  if (entries.length === 0) return null;
  return (
    <div className="alert alert-danger" role="alert" aria-labelledby={id}>
      <p id={id} className="fw-semibold mb-1">Please fix the following:</p>
      <ul className="mb-0">
        {entries.map(([field, message]) => <li key={field}><a href={`#${field}`}>{message}</a></li>)}
      </ul>
    </div>
  );
}

export function FormActions({ children }) {
  return <div className="form-actions d-flex flex-wrap gap-2 justify-content-end mt-3">{children}</div>;
}

/** Maps API fieldErrors to a { field: message } object. */
export function fieldErrorMap(apiError) {
  const map = {};
  (apiError?.fieldErrors || []).forEach((f) => { map[f.field] = f.message; });
  return map;
}
