import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { LoanApplicationWizard, toPayload, validateStep } from "./LoanApplicationWizard";

const product = { id: "p1", name: "Personal Flex", annualRate: 12, minAmount: 50000, maxAmount: 1500000, minMonths: 6, maxMonths: 60 };
const profile = { fullName: "Asha", dateOfBirth: "1996-05-10", addressLine1: "12 Test Street", city: "Pune", postalCode: "411001",
  employmentType: "SALARIED", monthlyIncome: 85000, existingMonthlyObligations: 12000 };
const baseForm = { productId: "p1", requestedAmount: "300000.00", requestedMonths: "36", purpose: "Home improvement",
  employmentType: "SALARIED", employerName: "", employmentStartDate: "", monthlyIncome: "85000", existingMonthlyObligations: "12000",
  monthlyExpenses: "" };

describe("validateStep", () => {
  test("personal step requires a complete profile", () => {
    expect(validateStep(0, baseForm, product, { ...profile, dateOfBirth: null }).profile).toBeDefined();
    expect(validateStep(0, baseForm, product, profile)).toEqual({});
  });
  test("income is required", () => {
    expect(validateStep(1, { ...baseForm, monthlyIncome: "" }, product, profile).monthlyIncome).toMatch(/required/i);
  });
  test("amount and tenure must be within product limits", () => {
    const e = validateStep(3, { ...baseForm, requestedAmount: "10000", requestedMonths: "72" }, product, profile);
    expect(e.requestedAmount).toMatch(/between/i);
    expect(e.requestedMonths).toMatch(/6-60/);
    expect(validateStep(3, baseForm, product, profile)).toEqual({});
  });
  test("payload sends validated money as numbers and never a customer id", () => {
    const p = toPayload(baseForm);
    expect(p.requestedAmount).toBe(300000);
    expect(p.requestedMonths).toBe(36);
    expect(p.monthlyExpenses).toBeNull();
    expect(p).not.toHaveProperty("customerId");
  });
});

function renderWizard(props = {}) {
  const onSaveDraft = vi.fn().mockResolvedValue("app-1");
  const onSubmit = vi.fn().mockResolvedValue();
  render(
    <MemoryRouter>
      <LoanApplicationWizard products={[product]} profile={profile} initial={{ productId: "p1" }}
        onSaveDraft={onSaveDraft} onSubmit={onSubmit} {...props} />
    </MemoryRouter>
  );
  return { onSaveDraft, onSubmit };
}

test("cannot move past employment without income", async () => {
  const user = userEvent.setup();
  renderWizard({ profile: { ...profile, monthlyIncome: null } });
  await user.click(screen.getByRole("button", { name: "Next" }));
  expect(screen.getByText(/step 2 of 5/i)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Next" }));
  expect(screen.getByText(/step 2 of 5/i)).toBeInTheDocument();
  expect(screen.getAllByText(/monthly income is required/i).length).toBeGreaterThan(0);
});

test("saves a draft at any step", async () => {
  const user = userEvent.setup();
  const { onSaveDraft } = renderWizard();
  await user.click(screen.getByRole("button", { name: /save draft/i }));
  expect(onSaveDraft).toHaveBeenCalledWith(null, expect.objectContaining({ productId: "p1" }));
  expect(await screen.findByText(/draft saved/i)).toBeInTheDocument();
});

test("completes all steps, requires the declaration and submits", async () => {
  const user = userEvent.setup();
  const { onSaveDraft, onSubmit } = renderWizard();
  await user.click(screen.getByRole("button", { name: "Next" })); // personal -> employment
  await user.click(screen.getByRole("button", { name: "Next" })); // employment -> obligations
  await user.click(screen.getByRole("button", { name: "Next" })); // obligations -> loan
  await user.type(screen.getByLabelText(/purpose/i), "Home improvement");
  await user.type(screen.getByLabelText(/^amount/i), "300000");
  await user.type(screen.getByLabelText(/tenure/i), "36");
  expect(screen.getByText(/estimated emi/i)).toHaveTextContent(/9,964\.29/);
  await user.click(screen.getByRole("button", { name: "Next" })); // loan -> review
  const submitButton = screen.getByRole("button", { name: /submit application/i });
  expect(submitButton).toBeDisabled();
  await user.click(screen.getByLabelText(/i confirm the information is accurate/i));
  await user.click(submitButton);
  await user.click(screen.getByRole("button", { name: "Submit" }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("app-1"));
  expect(onSaveDraft).toHaveBeenCalledWith(null, expect.objectContaining({ requestedAmount: 300000, requestedMonths: 36 }));
});
