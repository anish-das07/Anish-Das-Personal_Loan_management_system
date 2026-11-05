import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LoginForm } from "./LoginForm";

// Representative test from the case study (section 14), unchanged in intent.
test("requires email and password before submit", async () => {
  const user = userEvent.setup();
  render(<LoginForm onSubmit={vi.fn()} />);
  await user.click(screen.getByRole("button", { name: /sign in/i }));
  expect(screen.getByText(/email is required/i)).toBeInTheDocument();
  expect(screen.getByText(/password is required/i)).toBeInTheDocument();
});

test("does not call onSubmit when validation fails and marks fields invalid", async () => {
  const user = userEvent.setup();
  const onSubmit = vi.fn();
  render(<LoginForm onSubmit={onSubmit} />);
  await user.type(screen.getByLabelText(/email address/i), "not-an-email");
  await user.click(screen.getByRole("button", { name: /sign in/i }));
  expect(onSubmit).not.toHaveBeenCalled();
  expect(screen.getByText(/enter a valid email address/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/email address/i)).toHaveAttribute("aria-invalid", "true");
});

test("submits trimmed credentials and disables the button while pending", async () => {
  const user = userEvent.setup();
  let resolve;
  const onSubmit = vi.fn(() => new Promise((r) => { resolve = r; }));
  render(<LoginForm onSubmit={onSubmit} />);
  await user.type(screen.getByLabelText(/email address/i), "  customer@example.test ");
  await user.type(screen.getByLabelText(/^password/i), "Secret-123");
  await user.click(screen.getByRole("button", { name: /sign in/i }));
  expect(onSubmit).toHaveBeenCalledWith({ email: "customer@example.test", password: "Secret-123" });
  expect(screen.getByRole("button", { name: /signing in/i })).toBeDisabled();
  resolve();
  expect(await screen.findByRole("button", { name: /sign in/i })).toBeEnabled();
});

test("shows a generic error message passed by the page", () => {
  render(<LoginForm onSubmit={vi.fn()} error="Invalid email or password." />);
  expect(screen.getByRole("alert")).toHaveTextContent("Invalid email or password.");
});

test("password visibility toggle is keyboard accessible", async () => {
  const user = userEvent.setup();
  render(<LoginForm onSubmit={vi.fn()} />);
  const toggle = screen.getByRole("button", { name: /show password/i });
  await user.click(toggle);
  expect(screen.getByLabelText(/^password/i)).toHaveAttribute("type", "text");
  expect(screen.getByRole("button", { name: /hide password/i })).toHaveAttribute("aria-pressed", "true");
});
