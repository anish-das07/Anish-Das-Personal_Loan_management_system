import { useState } from "react";
import { FormActions, FormField, fieldProps } from "../../components/forms";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Login form (section 8.1). Validates email and password, disables submit while pending and shows a
 * generic error for invalid credentials so it never reveals which part was wrong.
 */
export function LoginForm({ onSubmit, error }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState({});
  const [pending, setPending] = useState(false);

  const validate = () => {
    const e = {};
    if (!email.trim()) e.email = "Email is required";
    else if (!EMAIL.test(email.trim())) e.email = "Enter a valid email address";
    if (!password) e.password = "Password is required";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!validate()) return;
    setPending(true);
    try {
      await onSubmit({ email: email.trim(), password });
    } finally {
      setPending(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate aria-label="Sign in">
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      <FormField id="email" label="Email address" error={errors.email} required>
        <input {...fieldProps("email", errors.email)} type="email" autoComplete="username" value={email}
          onChange={(e) => setEmail(e.target.value)} />
      </FormField>
      <FormField id="password" label="Password" error={errors.password} required>
        <div className="input-group">
          <input {...fieldProps("password", errors.password)} type={showPassword ? "text" : "password"}
            autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          <button type="button" className="btn btn-outline-secondary" onClick={() => setShowPassword((s) => !s)}
            aria-pressed={showPassword} aria-label={showPassword ? "Hide password" : "Show password"}>
            {showPassword ? "Hide" : "Show"}
          </button>
        </div>
      </FormField>
      <FormActions>
        <button type="submit" className="btn btn-primary w-100" disabled={pending}>
          {pending ? "Signing in…" : "Sign in"}
        </button>
      </FormActions>
    </form>
  );
}
