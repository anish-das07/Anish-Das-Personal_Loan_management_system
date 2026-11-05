/**
 * EMI preview helper (case study section 9). UI preview only: the server recalculates with
 * BigDecimal and a documented rounding policy, and its schedule is the source of truth.
 */
export function calculateEmi(principal, annualRatePercent, months) {
  const P = Number(principal);
  const n = Number(months);
  const annualRate = Number(annualRatePercent);
  if (!(P > 0) || !(n > 0) || !Number.isInteger(n) || !(annualRate >= 0)) {
    throw new Error("Enter a positive principal and tenure, and a non-negative rate.");
  }
  const r = annualRate / 1200;
  if (r === 0) return Math.round((P / n) * 100) / 100;
  const factor = Math.pow(1 + r, n);
  return Math.round(((P * r * factor) / (factor - 1)) * 100) / 100;
}

/** Totals for display next to the estimate (approximate; server totals are authoritative). */
export function estimateTotals(principal, annualRatePercent, months) {
  const emi = calculateEmi(principal, annualRatePercent, months);
  const totalPayment = Math.round(emi * Number(months) * 100) / 100;
  return { emi, totalPayment, totalInterest: Math.round((totalPayment - Number(principal)) * 100) / 100 };
}
