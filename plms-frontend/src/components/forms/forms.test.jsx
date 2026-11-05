import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CurrencyInput, FileUpload, FormField, StepIndicator, ValidationSummary, fieldProps, isValidMoney } from "./index";

describe("FormField", () => {
  test("associates label, required marker and error message", () => {
    render(
      <FormField id="monthlyIncome" label="Monthly income" error="Monthly income is required" required>
        <input {...fieldProps("monthlyIncome", "Monthly income is required")} />
      </FormField>
    );
    const input = screen.getByLabelText(/monthly income \*/i);
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-describedby", "monthlyIncome-error");
    expect(screen.getByRole("alert")).toHaveTextContent("Monthly income is required");
  });

  test("valid field has no error wiring", () => {
    render(<FormField id="city" label="City"><input {...fieldProps("city", null)} /></FormField>);
    expect(screen.getByLabelText("City")).toHaveAttribute("aria-invalid", "false");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("CurrencyInput", () => {
  test("keeps the typed value as text and announces the currency", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<FormField id="amt" label="Amount"><CurrencyInput id="amt" value="" onChange={onChange} /></FormField>);
    await user.type(screen.getByLabelText("Amount"), "5");
    expect(onChange).toHaveBeenCalledWith("5");
    expect(screen.getByText(/amount in inr/i)).toBeInTheDocument();
  });

  test("isValidMoney accepts two-decimal positive amounts only", () => {
    expect(isValidMoney("300000.00")).toBe(true);
    expect(isValidMoney("3,00,000")).toBe(true);
    expect(isValidMoney("0")).toBe(false);
    expect(isValidMoney("0", { allowZero: true })).toBe(true);
    expect(isValidMoney("12.345")).toBe(false);
    expect(isValidMoney("-5")).toBe(false);
    expect(isValidMoney("abc")).toBe(false);
  });
});

describe("FileUpload", () => {
  const file = (name, size, type = "application/pdf") => {
    const f = new File(["x"], name, { type });
    Object.defineProperty(f, "size", { value: size });
    return f;
  };

  test("rejects a disallowed type with a hint and does not select it", () => {
    const onSelect = vi.fn();
    render(<FileUpload id="doc" onSelect={onSelect} />);
    fireEvent.change(document.getElementById("doc"), { target: { files: [file("tool.exe", 10, "application/octet-stream")] } });
    expect(onSelect).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent(/not accepted/i);
  });

  test("rejects an oversized file", () => {
    const onSelect = vi.fn();
    render(<FileUpload id="doc" maxBytes={1024} onSelect={onSelect} />);
    fireEvent.change(document.getElementById("doc"), { target: { files: [file("big.pdf", 2048)] } });
    expect(onSelect).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent(/larger than/i);
  });

  test("accepts an allowed file", () => {
    const onSelect = vi.fn();
    render(<FileUpload id="doc" onSelect={onSelect} />);
    const ok = file("salary.PDF", 2048);
    fireEvent.change(document.getElementById("doc"), { target: { files: [ok] } });
    expect(onSelect).toHaveBeenCalledWith(ok);
  });
});

test("StepIndicator marks the current step programmatically", () => {
  render(<StepIndicator steps={["Personal", "Employment", "Loan"]} currentStep={1} />);
  const current = screen.getByText("Employment").closest("li");
  expect(current).toHaveAttribute("aria-current", "step");
  expect(screen.getByText("Personal").closest("li")).not.toHaveAttribute("aria-current");
});

test("ValidationSummary links each error to its field", () => {
  render(<ValidationSummary errors={{ monthlyIncome: "Monthly income is required", purpose: null }} />);
  const link = screen.getByRole("link", { name: "Monthly income is required" });
  expect(link).toHaveAttribute("href", "#monthlyIncome");
  expect(screen.getAllByRole("listitem")).toHaveLength(1);
});
