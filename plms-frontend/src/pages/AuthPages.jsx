import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { LoginForm } from "../features/auth/LoginForm";
import { RegistrationForm } from "../features/auth/RegistrationForm";
import { authApi } from "../features/auth/authApi";
import { FormActions, FormField, fieldProps } from "../components/forms";
import { homePathFor } from "../utils/status";

function AuthCard({ title, children, footer }) {
  return (
    <div className="auth-page d-flex flex-column align-items-center justify-content-center px-3 py-5">
      <div className="d-flex w-100 justify-content-between auth-brand mb-3">
        <span className="fw-bold">PLMS</span>
        <a href="#accessibility" className="small">Help / Accessibility</a>
      </div>
      <main id="main-content" className="card auth-card shadow-sm w-100">
        <div className="card-body p-4">
          <h1 className="h4 mb-3">{title}</h1>
          {children}
        </div>
        {footer && <div className="card-footer small bg-body">{footer}</div>}
      </main>
    </div>
  );
}

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState(null);
  if (user) return <Navigate to={homePathFor(user)} replace />;

  const submit = async ({ email, password }) => {
    setError(null);
    try {
      const u = await login(email, password);
      navigate(location.state?.from?.pathname ?? homePathFor(u), { replace: true });
    } catch (e) {
      setError(e?.status === 429 ? e.message : e?.status === 401 ? "Invalid email or password." : (e?.message ?? "Sign-in failed."));
    }
  };

  return (
    <AuthCard title="Welcome back" footer="Security note: never share your password or OTP.">
      <LoginForm onSubmit={submit} error={error} />
      <div className="d-flex justify-content-between mt-3 small">
        <Link to="/forgot-password">Forgot password?</Link>
        <Link to="/register">Create customer account</Link>
      </div>
    </AuthCard>
  );
}

export function RegistrationPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const submit = async (payload) => {
    await authApi.register(payload);
    await login(payload.email, payload.password);
    navigate("/app/profile", { replace: true, state: { welcome: true } });
  };
  return (
    <AuthCard title="Create your PLMS account" footer={<>Already registered? <Link to="/login">Sign in</Link></>}>
      <RegistrationForm onSubmit={submit} />
    </AuthCard>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState(null);
  const submit = async (e) => {
    e.preventDefault();
    if (!email.trim()) { setError("Email is required"); return; }
    setError(null);
    try {
      await authApi.requestReset(email.trim());
    } catch {
      // Same neutral message on failure, so the response never reveals account existence.
    } finally {
      setSent(true); // Neutral confirmation whether or not the address is registered.
    }
  };
  return (
    <AuthCard title="Forgot password" footer={<Link to="/login">Back to sign in</Link>}>
      {sent ? (
        <div className="alert alert-info" role="status">If the address is registered, reset instructions have been sent.</div>
      ) : (
        <form onSubmit={submit} noValidate>
          <FormField id="resetEmail" label="Email" error={error} required>
            <input {...fieldProps("resetEmail", error)} type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </FormField>
          <FormActions><button type="submit" className="btn btn-primary">Send reset instructions</button></FormActions>
        </form>
      )}
    </AuthCard>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(password)) { setError("Use 8-72 characters with a letter and a digit."); return; }
    if (password !== confirm) { setError("Passwords do not match."); return; }
    try {
      await authApi.confirmReset(token, password);
      setDone(true);
    } catch (err) {
      setError(err?.message ?? "This reset link is invalid or has expired.");
    }
  };
  return (
    <AuthCard title="Reset password" footer={<Link to="/login">Back to sign in</Link>}>
      {done ? <div className="alert alert-success" role="status">Password updated. You can now sign in.</div> : (
        <form onSubmit={submit} noValidate>
          {error && <div className="alert alert-danger" role="alert">{error}</div>}
          {!token && <div className="alert alert-warning">Open the link from your reset email to continue.</div>}
          <FormField id="newPassword" label="New password" required>
            <input {...fieldProps("newPassword", null)} type="password" autoComplete="new-password" value={password}
              onChange={(e) => setPassword(e.target.value)} />
          </FormField>
          <FormField id="confirmPassword" label="Confirm password" required>
            <input {...fieldProps("confirmPassword", null)} type="password" autoComplete="new-password" value={confirm}
              onChange={(e) => setConfirm(e.target.value)} />
          </FormField>
          <FormActions><button type="submit" className="btn btn-primary" disabled={!token}>Reset</button></FormActions>
        </form>
      )}
    </AuthCard>
  );
}

export function ForbiddenPage() {
  return (
    <div className="p-5 text-center">
      <h1 className="h3">Access denied</h1>
      <p>You do not have permission to view this page.</p>
      <Link to="/">Go to your home page</Link>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="p-5 text-center">
      <h1 className="h3">Page not found</h1>
      <Link to="/">Go to your home page</Link>
    </div>
  );
}
