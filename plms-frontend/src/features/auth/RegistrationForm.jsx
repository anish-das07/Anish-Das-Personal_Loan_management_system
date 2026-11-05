import { useState } from "react";
import { FormActions, FormField, ValidationSummary, fieldProps, fieldErrorMap } from "../../components/forms";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

/** Customer registration (section 8.2). There is no role field: the server always assigns CUSTOMER. */
export function RegistrationForm({ onSubmit }) {
  const [form, setForm] = useState({ fullName: "", email: "", mobile: "", password: "", confirm: "", acceptedTerms: false });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState(null);
  const [pending, setPending] = useState(false);
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.type === "checkbox" ? e.target.checked : e.target.value }));

  const validate = () => {
    const e = {};
    if (!form.fullName.trim()) e.fullName = "Full name is required";
    if (!EMAIL.test(form.email.trim())) e.email = "Enter a valid email address";
    if (!/^[0-9+][0-9 -]{6,19}$/.test(form.mobile.trim())) e.mobile = "Enter a valid mobile number";
    if (!PASSWORD.test(form.password)) e.password = "Use 8-72 characters with at least one letter and one digit";
    if (form.confirm !== form.password) e.confirm = "Passwords do not match";
    if (!form.acceptedTerms) e.acceptedTerms = "Accept the terms and privacy notice";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = async (event) => {
    event.preventDefault();
    setServerError(null);
    if (!validate()) return;
    setPending(true);
    try {
      await onSubmit({ fullName: form.fullName.trim(), email: form.email.trim(), mobile: form.mobile.trim(),
        password: form.password, acceptedTerms: true });
    } catch (err) {
      setServerError(err?.message ?? "Registration failed.");
      setErrors(fieldErrorMap(err));
    } finally {
      setPending(false);
    }
  };

  const strength = PASSWORD.test(form.password) ? (form.password.length >= 12 ? "Strong" : "Good") : form.password ? "Too weak" : "";

  return (
    <form onSubmit={submit} noValidate aria-label="Create account">
      <ValidationSummary errors={errors} />
      {serverError && <div className="alert alert-danger" role="alert">{serverError}</div>}
      <FormField id="fullName" label="Full name" error={errors.fullName} required>
        <input {...fieldProps("fullName", errors.fullName)} autoComplete="name" value={form.fullName} onChange={set("fullName")} />
      </FormField>
      <FormField id="email" label="Email" error={errors.email} required>
        <input {...fieldProps("email", errors.email)} type="email" autoComplete="email" value={form.email} onChange={set("email")} />
      </FormField>
      <FormField id="mobile" label="Mobile number" error={errors.mobile} required>
        <input {...fieldProps("mobile", errors.mobile)} type="tel" autoComplete="tel" value={form.mobile} onChange={set("mobile")} />
      </FormField>
      <FormField id="password" label="Password" error={errors.password} hint={strength && `Strength: ${strength}`} required>
        <input {...fieldProps("password", errors.password)} type="password" autoComplete="new-password" value={form.password}
          onChange={set("password")} />
      </FormField>
      <FormField id="confirm" label="Confirm password" error={errors.confirm} required>
        <input {...fieldProps("confirm", errors.confirm)} type="password" autoComplete="new-password" value={form.confirm}
          onChange={set("confirm")} />
      </FormField>
      <div className="form-check mb-2">
        <input id="acceptedTerms" type="checkbox" className={`form-check-input${errors.acceptedTerms ? " is-invalid" : ""}`}
          checked={form.acceptedTerms} onChange={set("acceptedTerms")} aria-invalid={Boolean(errors.acceptedTerms)} />
        <label htmlFor="acceptedTerms" className="form-check-label">I accept the terms and privacy notice</label>
      </div>
      <FormActions>
        <button type="submit" className="btn btn-primary w-100" disabled={pending}>{pending ? "Creating…" : "Create account"}</button>
      </FormActions>
    </form>
  );
}
