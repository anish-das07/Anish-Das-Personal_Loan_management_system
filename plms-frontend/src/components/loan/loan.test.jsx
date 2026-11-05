import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { EMICalculator } from "./EMICalculator";
import { AmortizationScheduleTable, ApprovalActionDialog, DocumentChecklist, EligibilityResultCard, LoanClosurePanel,
  LoanStatusTimeline } from "./index";

describe("EMICalculator", () => {
  const quoteResult = {
    monthlyEmi: 9964.29, totalInterest: 58714.58, totalPayment: 358714.58, disclaimer: "Estimate only; final approved terms may differ.",
    roundingPolicy: "HALF_UP", installments: [
      { installmentNo: 1, dueDate: "2026-11-23", openingBalance: 300000, principal: 6964.29, interest: 3000, total: 9964.29, closingBalance: 293035.71 }
    ]
  };

  test("shows an instant estimate and the server quote", async () => {
    const user = userEvent.setup();
    const quote = vi.fn().mockResolvedValue(quoteResult);
    render(<EMICalculator quote={quote} initialPrincipal="300000.00" initialRate="12" initialMonths={36} />);
    expect(screen.getByTestId("emi-preview")).toHaveTextContent(/9,964\.29/);
    await user.click(screen.getByRole("button", { name: /calculate/i }));
    expect(quote).toHaveBeenCalledWith({ principal: 300000, annualRate: 12, months: 36, productId: undefined });
    expect(await screen.findByText(/total interest/i)).toBeInTheDocument();
    expect(screen.getByRole("table", { name: /amortisation schedule/i })).toBeInTheDocument();
  });

  test("validates against product limits before calling the server", async () => {
    const user = userEvent.setup();
    const quote = vi.fn();
    const product = { id: "p1", annualRate: 12, minAmount: 50000, maxAmount: 1500000, minMonths: 6, maxMonths: 60 };
    render(<EMICalculator product={product} quote={quote} initialPrincipal="10000.00" initialMonths={72} />);
    await user.click(screen.getByRole("button", { name: /calculate/i }));
    expect(quote).not.toHaveBeenCalled();
    expect(screen.getByText(/amount must be between/i)).toBeInTheDocument();
    expect(screen.getByText(/tenure must be 6-60 months/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/annual rate/i)).toHaveAttribute("readonly");
  });
});

describe("ApprovalActionDialog", () => {
  const application = { requestedAmount: 300000, requestedMonths: 36, version: 4 };

  test("requires a reason code", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<ApprovalActionDialog open decision="APPROVE" application={application} onConfirm={onConfirm} onCancel={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /confirm decision/i }));
    expect(onConfirm).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent(/select a reason code/i);
  });

  test("submits the decision with amount, tenure and optimistic version", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn().mockResolvedValue();
    render(<ApprovalActionDialog open decision="APPROVE" application={application} onConfirm={onConfirm} onCancel={vi.fn()} />);
    expect(screen.getByRole("dialog")).toHaveAccessibleName(/approve/i);
    await user.selectOptions(screen.getByLabelText(/reason code/i), "POLICY_MET");
    const amount = screen.getByLabelText(/approved amount/i);
    await user.clear(amount);
    await user.type(amount, "250000");
    await user.click(screen.getByRole("button", { name: /confirm decision/i }));
    expect(onConfirm).toHaveBeenCalledWith(expect.objectContaining({
      decision: "APPROVE", reasonCode: "POLICY_MET", approvedAmount: 250000, approvedMonths: 36, expectedVersion: 4
    }));
  });

  test("Escape cancels and focus starts inside the dialog", async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(<ApprovalActionDialog open decision="REJECT" application={application} onConfirm={vi.fn()} onCancel={onCancel} />);
    expect(screen.getByRole("dialog")).toContainElement(document.activeElement);
    await user.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalled();
  });

  test("surfaces a server conflict such as a stale decision", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn().mockRejectedValue({ message: "The application changed since it was loaded. Reload and retry." });
    render(<ApprovalActionDialog open decision="REJECT" application={application} onConfirm={onConfirm} onCancel={vi.fn()} />);
    await user.selectOptions(screen.getByLabelText(/reason code/i), "POLICY_NOT_MET");
    await user.click(screen.getByRole("button", { name: /confirm decision/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/reload and retry/i);
  });
});

test("LoanStatusTimeline marks the current stage", () => {
  render(<LoanStatusTimeline status="UNDER_REVIEW" />);
  expect(screen.getByText("Review").closest("li")).toHaveAttribute("aria-current", "step");
});

test("DocumentChecklist shows status text and upload actions only where allowed", async () => {
  const user = userEvent.setup();
  const onUpload = vi.fn();
  const items = [
    { category: "IDENTITY_PROOF", mandatory: true, status: "VERIFIED", version: 1 },
    { category: "INCOME_PROOF", mandatory: true, status: "RESUBMISSION_REQUIRED", version: 1, reviewerComment: "Upload a clearer copy." },
    { category: "BANK_STATEMENT", mandatory: false, status: "MISSING" }
  ];
  render(<DocumentChecklist items={items} canUpload onUpload={onUpload} />);
  expect(screen.getByText("Verified")).toBeInTheDocument();
  expect(screen.getByText("Resubmit")).toBeInTheDocument();
  expect(screen.getByText(/upload a clearer copy/i)).toBeInTheDocument();
  expect(screen.getAllByRole("button")).toHaveLength(2);
  await user.click(screen.getByRole("button", { name: "Upload" }));
  expect(onUpload).toHaveBeenCalledWith("BANK_STATEMENT");
});

test("EligibilityResultCard uses neutral wording", () => {
  render(<EligibilityResultCard assessment={{ outcome: "MANUAL_REVIEW", policyVersion: "PERSONAL-2026.1", passed: 5,
    needsReview: 1, pending: 0, failed: 0, evaluatedAt: "2026-10-23T09:30:00Z" }} />);
  expect(screen.getByText("Manual review")).toBeInTheDocument();
  expect(screen.getByText(/not a loan approval/i)).toBeInTheDocument();
});

test("AmortizationScheduleTable has accessible headers", () => {
  render(<AmortizationScheduleTable rows={[{ installmentNo: 1, dueDate: "2026-11-23", openingBalance: 300000, principal: 6964.29,
    interest: 3000, total: 9964.29, closingBalance: 293035.71, status: "DUE" }]} />);
  expect(screen.getByRole("columnheader", { name: "Interest" })).toBeInTheDocument();
  expect(screen.getByText("Due")).toBeInTheDocument();
});

test("LoanClosurePanel disables closure while blockers remain", () => {
  render(<MemoryRouter><LoanClosurePanel check={{ loanAccountId: "abc12345", closable: false, blockers: ["3 installment(s) are unpaid"] }}
    onClose={vi.fn()} /></MemoryRouter>);
  expect(screen.getByRole("button", { name: /request closure/i })).toBeDisabled();
  expect(screen.getByText(/3 installment/)).toBeInTheDocument();
});
