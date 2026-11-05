const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2 });

/** Formats a money value received from the API (number or numeric string) for display only. */
export function formatMoney(value) {
  if (value === null || value === undefined || value === "") return "-";
  const n = Number(value);
  return Number.isFinite(n) ? inr.format(n) : "-";
}

/**
 * Validates money typed by a user without doing floating-point arithmetic on it.
 * Accepts up to 13 integer digits and 2 decimals; returns a normalised string or null.
 */
export function normaliseMoneyInput(text) {
  if (text === null || text === undefined) return null;
  const cleaned = String(text).replace(/[,\s₹]/g, "");
  if (!/^\d{1,13}(\.\d{1,2})?$/.test(cleaned)) return null;
  const [whole, fraction = ""] = cleaned.split(".");
  return `${String(Number(whole))}.${fraction.padEnd(2, "0")}`;
}

export function formatDate(value) {
  if (!value) return "-";
  const d = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  if (Number.isNaN(d.getTime())) return "-";
  return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

export function formatDateTime(value) {
  if (!value) return "-";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "-";
  return d.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function shortRef(prefix, id) {
  return id ? `${prefix}-${String(id).slice(0, 8).toUpperCase()}` : "-";
}

/** Random idempotency key for money-like operations (one per logical attempt). */
export function newIdempotencyKey(prefix) {
  const random = globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${random}`;
}
