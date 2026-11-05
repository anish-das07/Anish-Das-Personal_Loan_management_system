import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RegistrationForm } from "./RegistrationForm";

async function fill(user, overrides = {}) {
  const v = { fullName: "Asha Customer", email: "asha@example.test", mobile: "+91 98765 43210", password: "Secret-123",
    confirm: "Secret-123", ...overrides };
  await user.type(screen.getByLabelText(/full name/i), v.fullName);
  await user.type(screen.getByLabelText(/^email/i), v.email);
  await user.type(screen.getByLabelText(/mobile number/i), v.mobile);
  await user.type(screen.getByLabelText(/^password/i), v.password);
  await user.type(screen.getByLabelText(/confirm password/i), v.confirm);
}

test("there is no role selection on public registration", () => {
  render(<RegistrationForm onSubmit={vi.fn()} />);
  expect(screen.queryByLabelText(/role/i)).not.toBeInTheDocument();
});

test("validates matching passwords and terms", async () => {
  const user = userEvent.setup();
  const onSubmit = vi.fn();
  render(<RegistrationForm onSubmit={onSubmit} />);
  await fill(user, { confirm: "Different-123" });
  await user.click(screen.getByRole("button", { name: /create account/i }));
  expect(onSubmit).not.toHaveBeenCalled();
  expect(screen.getAllByText(/passwords do not match/i).length).toBeGreaterThan(0);
  expect(screen.getAllByText(/accept the terms/i).length).toBeGreaterThan(0);
});

test("submits a customer payload without any role", async () => {
  const user = userEvent.setup();
  const onSubmit = vi.fn().mockResolvedValue();
  render(<RegistrationForm onSubmit={onSubmit} />);
  await fill(user);
  await user.click(screen.getByLabelText(/i accept the terms/i));
  await user.click(screen.getByRole("button", { name: /create account/i }));
  expect(onSubmit).toHaveBeenCalledWith({ fullName: "Asha Customer", email: "asha@example.test", mobile: "+91 98765 43210",
    password: "Secret-123", acceptedTerms: true });
  expect(onSubmit.mock.calls[0][0]).not.toHaveProperty("role");
});

test("shows a safe server conflict message", async () => {
  const user = userEvent.setup();
  const onSubmit = vi.fn().mockRejectedValue({ status: 409, code: "ACCOUNT_EXISTS",
    message: "An account could not be created with these details. Try signing in instead.", fieldErrors: [] });
  render(<RegistrationForm onSubmit={onSubmit} />);
  await fill(user);
  await user.click(screen.getByLabelText(/i accept the terms/i));
  await user.click(screen.getByRole("button", { name: /create account/i }));
  expect(await screen.findByText(/try signing in instead/i)).toBeInTheDocument();
});
