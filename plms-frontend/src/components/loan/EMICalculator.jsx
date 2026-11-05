import { useState } from "react";
import { calculateEmi } from "../../utils/emi";
import { formatMoney, normaliseMoneyInput } from "../../utils/format";
import { InlineAlert, SummaryCard } from "../common";
import { CurrencyInput, FormField, fieldProps, isValidMoney } from "../forms";
import { AmortizationScheduleTable } from "./index";

/**
 * EMI calculator (sections 8.7 and 8.17). Shows an instant client-side estimate, then asks the server
 * for the authoritative quote and schedule when the user presses Calculate.
 */
export function EMICalculator({ product, initialPrincipal = "300000.00", initialRate, initialMonths = 36, quote }) {
  const [principal, setPrincipal] = useState(initialPrincipal);
  const [annualRate, setAnnualRate] = useState(String(initialRate ?? product?.annualRate ?? "12.00"));
  const [months, setMonths] = useState(String(initialMonths));
  const [errors, setErrors] = useState({});
  const [result, setResult] = useState(null);
  const [apiError, setApiError] = useState(null);
  const [busy, setBusy] = useState(false);

  const validate = () => {
    const e = {};
    if (!isValidMoney(principal)) e.principal = "Enter a positive principal.";
    else if (product && (Number(principal) < Number(product.minAmount) || Number(principal) > Number(product.maxAmount))) {
      e.principal = `Amount must be between ${formatMoney(product.minAmount)} and ${formatMoney(product.maxAmount)}.`;
    }
    if (!(Number(annualRate) >= 0) || annualRate === "") e.annualRate = "Rate must be zero or more.";
    const n = Number(months);
    if (!Number.isInteger(n) || n < 1) e.months = "Enter a whole number of months.";
    else if (product && (n < product.minMonths || n > product.maxMonths)) {
      e.months = `Tenure must be ${product.minMonths}-${product.maxMonths} months.`;
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  let preview = null;
  try {
    if (isValidMoney(principal) && Number(months) > 0 && Number(annualRate) >= 0) {
      preview = calculateEmi(principal, annualRate, Number(months));
    }
  } catch {
    preview = null;
  }

  const submit = async (event) => {
    event.preventDefault();
    if (!validate()) return;
    if (!quote) return;
    setBusy(true);
    setApiError(null);
    try {
      setResult(await quote({ principal: Number(normaliseMoneyInput(principal)), annualRate: Number(annualRate),
        months: Number(months), productId: product?.id }));
    } catch (e) {
      setApiError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-labelledby="emi-heading">
      <h2 id="emi-heading" className="h5">EMI calculator</h2>
      <form className="row g-2 align-items-end" onSubmit={submit} noValidate>
        <div className="col-md-4">
          <FormField id="principal" label="Principal" error={errors.principal} required>
            <CurrencyInput id="principal" value={principal} onChange={setPrincipal} error={errors.principal} />
          </FormField>
        </div>
        <div className="col-md-3">
          <FormField id="annualRate" label="Annual rate (%)" error={errors.annualRate} required>
            <input {...fieldProps("annualRate", errors.annualRate)} inputMode="decimal" value={annualRate}
              readOnly={Boolean(product)} onChange={(e) => setAnnualRate(e.target.value)} />
          </FormField>
        </div>
        <div className="col-md-3">
          <FormField id="months" label="Tenure (months)" error={errors.months} required>
            <input {...fieldProps("months", errors.months)} inputMode="numeric" value={months}
              onChange={(e) => setMonths(e.target.value)} />
          </FormField>
        </div>
        <div className="col-md-2 mb-3">
          <button type="submit" className="btn btn-primary w-100" disabled={busy}>{busy ? "Calculating…" : "Calculate"}</button>
        </div>
      </form>
      <p className="small text-body-secondary" aria-live="polite">
        Instant estimate: <strong data-testid="emi-preview">{preview != null ? formatMoney(preview) : "-"}</strong> per month.
      </p>
      <InlineAlert error={apiError} />
      {result && (
        <>
          <div className="row g-3 my-1">
            <div className="col-md-4"><SummaryCard label="Monthly EMI" value={formatMoney(result.monthlyEmi)} /></div>
            <div className="col-md-4"><SummaryCard label="Total interest" value={formatMoney(result.totalInterest)} /></div>
            <div className="col-md-4"><SummaryCard label="Total repayment" value={formatMoney(result.totalPayment)} /></div>
          </div>
          <AmortizationScheduleTable rows={result.installments} />
          <p className="small text-body-secondary">{result.disclaimer} Rounding: {result.roundingPolicy}.</p>
        </>
      )}
      {!result && <p className="small text-body-secondary">Estimate only; final approved terms may differ.</p>}
    </section>
  );
}
