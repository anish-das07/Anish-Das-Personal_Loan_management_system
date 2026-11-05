import { calculateEmi, estimateTotals } from "./emi";
import { formatMoney, normaliseMoneyInput, shortRef, newIdempotencyKey } from "./format";
import { homePathFor, isStaff, statusLabel, statusTone, timelineIndex } from "./status";

describe("calculateEmi", () => {
  test("matches the case study worked example", () => {
    expect(calculateEmi(300000, 12, 36)).toBe(9964.29);
  });
  test("handles zero rate", () => {
    expect(calculateEmi(1200, 0, 12)).toBe(100);
  });
  test("rejects invalid input", () => {
    expect(() => calculateEmi(0, 12, 36)).toThrow();
    expect(() => calculateEmi(1000, -1, 12)).toThrow();
    expect(() => calculateEmi(1000, 12, 0)).toThrow();
    expect(() => calculateEmi(1000, 12, 1.5)).toThrow();
  });
  test("estimates totals", () => {
    const t = estimateTotals(300000, 12, 36);
    expect(t.emi).toBe(9964.29);
    expect(t.totalPayment).toBe(358714.44);
    expect(t.totalInterest).toBe(58714.44);
  });
});

describe("money formatting", () => {
  test("formats INR with two decimals", () => {
    expect(formatMoney(9964.29)).toMatch(/9,964\.29/);
    expect(formatMoney(null)).toBe("-");
  });
  test("normalises typed money without floating-point arithmetic", () => {
    expect(normaliseMoneyInput("3,00,000")).toBe("300000.00");
    expect(normaliseMoneyInput("₹ 9964.2")).toBe("9964.20");
    expect(normaliseMoneyInput("12.345")).toBeNull();
    expect(normaliseMoneyInput("")).toBeNull();
  });
  test("short references and idempotency keys", () => {
    expect(shortRef("APP", "7d4c1234-aaaa")).toBe("APP-7D4C1234");
    const a = newIdempotencyKey("disb");
    expect(a).toMatch(/^disb-/);
    expect(newIdempotencyKey("disb")).not.toBe(a);
  });
});

describe("status helpers", () => {
  test("labels always have text and a tone", () => {
    expect(statusLabel("UNDER_REVIEW")).toBe("Under review");
    expect(statusLabel("REJECTED")).toBe("Not approved");
    expect(statusLabel("SOME_NEW_STATUS")).toBe("Some new status");
    expect(statusTone("OVERDUE")).toBe("danger");
  });
  test("timeline mapping", () => {
    expect(timelineIndex("DRAFT")).toBe(0);
    expect(timelineIndex("UNDER_REVIEW")).toBe(3);
    expect(timelineIndex("ACTIVE")).toBe(5);
  });
  test("home path by role", () => {
    expect(homePathFor(null)).toBe("/login");
    expect(homePathFor({ roles: ["CUSTOMER"] })).toBe("/app/dashboard");
    expect(homePathFor({ roles: ["ADMIN"] })).toBe("/admin/users");
    expect(homePathFor({ roles: ["FINANCE_OFFICER"] })).toBe("/staff/dashboard");
    expect(isStaff({ roles: ["CUSTOMER"] })).toBe(false);
  });
});
