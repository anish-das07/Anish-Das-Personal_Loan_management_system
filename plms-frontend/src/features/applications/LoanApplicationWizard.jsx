import { useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ConfirmDialog, InlineAlert } from "../../components/common";
import { CurrencyInput, FormActions, FormField, StepIndicator, ValidationSummary, fieldErrorMap, fieldProps,
  isValidMoney } from "../../components/forms";
import { formatMoney, normaliseMoneyInput } from "../../utils/format";
import { calculateEmi } from "../../utils/emi";

export const WIZARD_STEPS = ["Personal", "Employment", "Obligations", "Loan", "Review"];

/** Pure step validation, so it can be unit-tested and reused. The server revalidates every field. */
export function validateStep(step, form, product, profile) {
  const e = {};
  if (step === 0) {
    if (!profile?.dateOfBirth || !profile?.addressLine1 || !profile?.city || !profile?.postalCode) {
      e.profile = "Complete your personal and address details in My Profile first.";
    }
  }
  if (step === 1) {
    if (!form.employmentType) e.employmentType = "Select an employment type";
    if (!isValidMoney(form.monthlyIncome)) e.monthlyIncome = "Monthly income is required";
  }
  if (step === 2) {
    if (form.existingMonthlyObligations !== "" && !isValidMoney(form.existingMonthlyObligations, { allowZero: true })) {
      e.existingMonthlyObligations = "Enter a valid amount";
    }
    if (form.monthlyExpenses !== "" && !isValidMoney(form.monthlyExpenses, { allowZero: true })) {
      e.monthlyExpenses = "Enter a valid amount";
    }
  }
  if (step === 3) {
    if (!form.productId) e.productId = "Choose a loan product";
    if (!isValidMoney(form.requestedAmount)) e.requestedAmount = "Enter the amount you need";
    else if (product && (Number(form.requestedAmount) < Number(product.minAmount) || Number(form.requestedAmount) > Number(product.maxAmount))) {
      e.requestedAmount = `Amount must be between ${formatMoney(product.minAmount)} and ${formatMoney(product.maxAmount)}`;
    }
    const n = Number(form.requestedMonths);
    if (!Number.isInteger(n) || n < 1) e.requestedMonths = "Enter the tenure in months";
    else if (product && (n < product.minMonths || n > product.maxMonths)) {
      e.requestedMonths = `Tenure must be ${product.minMonths}-${product.maxMonths} months`;
    }
    if (!form.purpose.trim()) e.purpose = "Purpose is required";
  }
  return e;
}

export function toPayload(form) {
  // Validated decimal text becomes a JSON number only at the boundary (no client-side arithmetic).
  const money = (v) => (v === "" || v == null ? null : Number(normaliseMoneyInput(v)));
  return {
    productId: form.productId, requestedAmount: money(form.requestedAmount), requestedMonths: Number(form.requestedMonths) || null,
    purpose: form.purpose.trim(), employmentType: form.employmentType || null, employerName: form.employerName || null,
    employmentStartDate: form.employmentStartDate || null, monthlyIncome: money(form.monthlyIncome),
    existingMonthlyObligations: money(form.existingMonthlyObligations), monthlyExpenses: money(form.monthlyExpenses)
  };
}

/**
 * Multi-step application wizard (section 8.8/8.9). Saves drafts at any step, validates each step
 * before moving on, and requires the declaration before submission.
 */
export function LoanApplicationWizard({ products, profile, initial, onSaveDraft, onSubmit }) {
  const [step, setStep] = useState(0);
  const [form, setForm] = useState(() => ({
    productId: initial?.productId ?? "", requestedAmount: initial?.requestedAmount != null ? String(initial.requestedAmount) : "",
    requestedMonths: initial?.requestedMonths != null ? String(initial.requestedMonths) : "",
    purpose: initial?.purpose ?? "", employmentType: initial?.employmentType ?? profile?.employmentType ?? "SALARIED",
    employerName: initial?.employerName ?? profile?.employerName ?? "",
    employmentStartDate: initial?.employmentStartDate ?? profile?.employmentStartDate ?? "",
    monthlyIncome: String(initial?.monthlyIncome ?? profile?.monthlyIncome ?? ""),
    existingMonthlyObligations: String(initial?.existingMonthlyObligations ?? profile?.existingMonthlyObligations ?? ""),
    monthlyExpenses: initial?.monthlyExpenses != null ? String(initial.monthlyExpenses) : ""
  }));
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [savedMessage, setSavedMessage] = useState(null);
  const [declaration, setDeclaration] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const draftId = useRef(initial?.applicationId ?? null);
  const product = useMemo(() => products.find((p) => p.id === form.productId), [products, form.productId]);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const setValue = (k) => (v) => setForm((f) => ({ ...f, [k]: v }));

  const saveDraft = async () => {
    setSaving(true);
    setApiError(null);
    try {
      draftId.current = await onSaveDraft(draftId.current, toPayload(form));
      setSavedMessage("Draft saved.");
      return draftId.current;
    } catch (err) {
      setApiError(err);
      setErrors(fieldErrorMap(err));
      return null;
    } finally {
      setSaving(false);
    }
  };

  const next = () => {
    const e = validateStep(step, form, product, profile);
    setErrors(e);
    if (Object.keys(e).length === 0) { setSavedMessage(null); setStep((s) => Math.min(s + 1, WIZARD_STEPS.length - 1)); }
  };

  const submit = async () => {
    for (let s = 0; s < 4; s++) {
      const e = validateStep(s, form, product, profile);
      if (Object.keys(e).length) { setErrors(e); setStep(s); setConfirmOpen(false); return; }
    }
    const id = await saveDraft();
    if (!id) { setConfirmOpen(false); return; }
    try {
      await onSubmit(id);
    } catch (err) {
      setApiError(err);
    } finally {
      setConfirmOpen(false);
    }
  };

  let emiPreview = null;
  try {
    if (product && isValidMoney(form.requestedAmount) && Number(form.requestedMonths) > 0) {
      emiPreview = calculateEmi(form.requestedAmount, product.annualRate, Number(form.requestedMonths));
    }
  } catch { emiPreview = null; }

  return (
    <div className="card"><div className="card-body">
      <p className="small text-body-secondary mb-2">Step {step + 1} of {WIZARD_STEPS.length}: {WIZARD_STEPS[step]}</p>
      <StepIndicator steps={WIZARD_STEPS} currentStep={step} />
      <ValidationSummary errors={errors} />
      <InlineAlert error={apiError} />
      {savedMessage && <InlineAlert tone="success">{savedMessage}</InlineAlert>}

      {step === 0 && (
        <section aria-label="Personal details">
          <dl className="row small">
            <dt className="col-sm-3">Name</dt><dd className="col-sm-9">{profile?.fullName ?? "-"}</dd>
            <dt className="col-sm-3">Date of birth</dt><dd className="col-sm-9">{profile?.dateOfBirth ?? "Missing"}</dd>
            <dt className="col-sm-3">Address</dt>
            <dd className="col-sm-9">{profile?.addressLine1 ? `${profile.addressLine1}, ${profile.city} ${profile.postalCode}` : "Missing"}</dd>
          </dl>
          <p className="small">Personal details come from <Link to="/app/profile">My Profile</Link>.</p>
        </section>
      )}
      {step === 1 && (
        <section aria-label="Employment and income" className="row">
          <div className="col-md-6"><FormField id="employmentType" label="Employment type" error={errors.employmentType} required>
            <select {...fieldProps("employmentType", errors.employmentType, { className: "form-select" })} value={form.employmentType}
              onChange={set("employmentType")}>
              <option value="SALARIED">Salaried</option><option value="SELF_EMPLOYED">Self-employed</option><option value="OTHER">Other</option>
            </select></FormField></div>
          <div className="col-md-6"><FormField id="employerName" label="Employer">
            <input {...fieldProps("employerName", null)} value={form.employerName} onChange={set("employerName")} /></FormField></div>
          <div className="col-md-6"><FormField id="employmentStartDate" label="Start date">
            <input {...fieldProps("employmentStartDate", null)} type="date" value={form.employmentStartDate}
              onChange={set("employmentStartDate")} /></FormField></div>
          <div className="col-md-6"><FormField id="monthlyIncome" label="Monthly income" error={errors.monthlyIncome} required>
            <CurrencyInput id="monthlyIncome" value={form.monthlyIncome} onChange={setValue("monthlyIncome")} error={errors.monthlyIncome} />
          </FormField></div>
        </section>
      )}
      {step === 2 && (
        <section aria-label="Obligations" className="row">
          <div className="col-md-6"><FormField id="existingMonthlyObligations" label="Existing EMI total" error={errors.existingMonthlyObligations}>
            <CurrencyInput id="existingMonthlyObligations" value={form.existingMonthlyObligations}
              onChange={setValue("existingMonthlyObligations")} error={errors.existingMonthlyObligations} /></FormField></div>
          <div className="col-md-6"><FormField id="monthlyExpenses" label="Monthly expenses" error={errors.monthlyExpenses}>
            <CurrencyInput id="monthlyExpenses" value={form.monthlyExpenses} onChange={setValue("monthlyExpenses")}
              error={errors.monthlyExpenses} /></FormField></div>
        </section>
      )}
      {step === 3 && (
        <section aria-label="Loan details" className="row">
          <div className="col-md-6"><FormField id="productId" label="Loan product" error={errors.productId} required>
            <select {...fieldProps("productId", errors.productId, { className: "form-select" })} value={form.productId} onChange={set("productId")}>
              <option value="">Select…</option>
              {products.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.annualRate}% p.a.)</option>)}
            </select></FormField></div>
          <div className="col-md-6"><FormField id="purpose" label="Purpose" error={errors.purpose} required>
            <input {...fieldProps("purpose", errors.purpose)} maxLength={80} value={form.purpose} onChange={set("purpose")} /></FormField></div>
          <div className="col-md-6"><FormField id="requestedAmount" label="Amount" error={errors.requestedAmount} required
            hint={product ? `${formatMoney(product.minAmount)} - ${formatMoney(product.maxAmount)}` : undefined}>
            <CurrencyInput id="requestedAmount" value={form.requestedAmount} onChange={setValue("requestedAmount")}
              error={errors.requestedAmount} /></FormField></div>
          <div className="col-md-6"><FormField id="requestedMonths" label="Tenure (months)" error={errors.requestedMonths} required
            hint={product ? `${product.minMonths}-${product.maxMonths} months` : undefined}>
            <input {...fieldProps("requestedMonths", errors.requestedMonths)} inputMode="numeric" value={form.requestedMonths}
              onChange={set("requestedMonths")} /></FormField></div>
          {emiPreview != null && <p className="small" aria-live="polite">Estimated EMI: <strong>{formatMoney(emiPreview)}</strong> (estimate only)</p>}
        </section>
      )}
      {step === 4 && (
        <section aria-label="Review application">
          {[["Employment and income", 1, `${form.employmentType} · income ${formatMoney(form.monthlyIncome)}`],
            ["Obligations", 2, `Existing EMI ${formatMoney(form.existingMonthlyObligations || 0)} · expenses ${formatMoney(form.monthlyExpenses || 0)}`],
            ["Requested loan", 3, `${product?.name ?? "-"} · ${formatMoney(form.requestedAmount)} · ${form.requestedMonths} months · ${form.purpose}`]
          ].map(([title, target, text]) => (
            <div key={title} className="d-flex justify-content-between border-bottom py-2">
              <div><div className="fw-semibold small">{title}</div><div className="small">{text}</div></div>
              <button type="button" className="btn btn-link btn-sm" onClick={() => setStep(target)}>Edit</button>
            </div>
          ))}
          <p className="small mt-3">After submission you will be asked for identity and income documents.</p>
          <div className="form-check">
            <input id="declaration" type="checkbox" className="form-check-input" checked={declaration}
              onChange={(e) => setDeclaration(e.target.checked)} />
            <label htmlFor="declaration" className="form-check-label">I confirm the information is accurate and consent to assessment</label>
          </div>
        </section>
      )}

      <FormActions>
        <button type="button" className="btn btn-outline-secondary" onClick={saveDraft} disabled={saving}>
          {saving ? "Saving…" : "Save draft"}</button>
        <button type="button" className="btn btn-outline-secondary" onClick={() => setStep((s) => Math.max(0, s - 1))}
          disabled={step === 0}>Previous</button>
        {step < 4 ? <button type="button" className="btn btn-primary" onClick={next}>Next</button> : (
          <button type="button" className="btn btn-primary" disabled={!declaration} onClick={() => setConfirmOpen(true)}>
            Submit application</button>)}
      </FormActions>
      <ConfirmDialog open={confirmOpen} title="Submit application?" confirmLabel="Submit" busy={saving}
        onConfirm={submit} onCancel={() => setConfirmOpen(false)}>
        Once submitted, you can no longer edit the application unless it is returned for correction.
      </ConfirmDialog>
    </div></div>
  );
}
