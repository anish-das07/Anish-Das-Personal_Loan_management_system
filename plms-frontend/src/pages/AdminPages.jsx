import { useState } from "react";
import { PageHeader } from "../layouts/AppLayout";
import { AsyncContent, InlineAlert, Pagination, StatusBadge } from "../components/common";
import { FormActions, FormField, fieldErrorMap, fieldProps } from "../components/forms";
import { adminApi } from "../features/auth/authApi";
import { useApiState } from "../hooks/useApiState";
import { useAuth } from "../hooks/useAuth";
import { formatDate, formatDateTime, formatMoney } from "../utils/format";
import { ROLE_LABELS, STAFF_ROLES } from "../utils/status";

export function UsersPage() {
  const { user } = useAuth();
  const state = useApiState(() => adminApi.users(), []);
  const [form, setForm] = useState({ fullName: "", email: "", password: "", roles: [] });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);

  const toggleRole = (role) => setForm((f) => ({ ...f, roles: f.roles.includes(role) ? f.roles.filter((r) => r !== role) : [...f.roles, role] }));
  const create = async (e) => {
    e.preventDefault();
    setError(null);
    setMessage(null);
    if (form.roles.length === 0) { setErrors({ roles: "Select at least one role" }); return; }
    try {
      await adminApi.createStaff(form);
      setMessage(`Created ${form.email}`);
      setForm({ fullName: "", email: "", password: "", roles: [] });
      setErrors({});
      state.reload();
    } catch (err) {
      setError(err);
      setErrors(fieldErrorMap(err));
    }
  };
  const setStatus = async (u, status) => {
    try {
      await adminApi.updateUser(u.id, { status });
      state.reload();
    } catch (err) {
      setError(err);
    }
  };

  return (
    <>
      <PageHeader title="Users and roles" subtitle="Customers self-register; staff accounts are created here." />
      {message && <InlineAlert tone="success">{message}</InlineAlert>}
      <InlineAlert error={error} />
      <div className="row g-3">
        <div className="col-lg-8">
          <AsyncContent state={state}>
            <div className="card"><div className="table-responsive"><table className="table align-middle mb-0">
              <caption className="visually-hidden">Users</caption>
              <thead><tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Roles</th><th scope="col">Status</th>
                <th scope="col"><span className="visually-hidden">Actions</span></th></tr></thead>
              <tbody>{state.data?.map((u) => (
                <tr key={u.id}><td>{u.fullName}</td><td>{u.email}</td><td className="small">{u.roles.map((r) => ROLE_LABELS[r]).join(", ")}</td>
                  <td><StatusBadge status={u.status === "ACTIVE" ? "ACTIVE" : "CLOSED"} label={u.status} /></td>
                  <td>{u.id !== user?.id && (u.status === "ACTIVE"
                    ? <button type="button" className="btn btn-sm btn-outline-danger" onClick={() => setStatus(u, "DISABLED")}>Disable</button>
                    : <button type="button" className="btn btn-sm btn-outline-success" onClick={() => setStatus(u, "ACTIVE")}>Enable</button>)}</td>
                </tr>))}</tbody></table></div></div>
          </AsyncContent>
        </div>
        <div className="col-lg-4">
          <form className="card" onSubmit={create} noValidate><div className="card-body">
            <h2 className="h6">Create staff user</h2>
            <FormField id="staffName" label="Full name" error={errors.fullName} required>
              <input {...fieldProps("staffName", errors.fullName)} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></FormField>
            <FormField id="staffEmail" label="Email" error={errors.email} required>
              <input {...fieldProps("staffEmail", errors.email)} type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></FormField>
            <FormField id="staffPassword" label="Initial password" error={errors.password} required>
              <input {...fieldProps("staffPassword", errors.password)} type="password" autoComplete="new-password" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })} /></FormField>
            <fieldset><legend className="form-label">Roles *</legend>
              {STAFF_ROLES.map((r) => (
                <div className="form-check" key={r}>
                  <input id={`role-${r}`} type="checkbox" className="form-check-input" checked={form.roles.includes(r)} onChange={() => toggleRole(r)} />
                  <label htmlFor={`role-${r}`} className="form-check-label">{ROLE_LABELS[r]}</label>
                </div>))}
              {errors.roles && <p role="alert" className="text-danger small">{errors.roles}</p>}
            </fieldset>
            <FormActions><button type="submit" className="btn btn-primary">Create</button></FormActions>
          </div></form>
        </div>
      </div>
    </>
  );
}

const EMPTY_PRODUCT = { code: "", name: "", description: "", purpose: "", minAmount: "", maxAmount: "", minMonths: "", maxMonths: "",
  annualRate: "", processingFeePercent: "", activeFrom: new Date().toISOString().slice(0, 10), activeTo: "", active: true,
  policyVersion: "PERSONAL-2026.1" };

export function ProductsAdminPage() {
  const products = useApiState(() => adminApi.products(), []);
  const policies = useApiState(() => adminApi.policies(), []);
  const [form, setForm] = useState(EMPTY_PRODUCT);
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [mode, setMode] = useState("NORMAL");

  const edit = (p) => {
    setEditingId(p.id);
    setForm(Object.fromEntries(Object.keys(EMPTY_PRODUCT).map((k) => [k, p[k] ?? (k === "active" ? false : "")])));
  };
  const save = async (e) => {
    e.preventDefault();
    setError(null);
    const num = (v) => (v === "" ? null : Number(v));
    const payload = { ...form, minAmount: num(form.minAmount), maxAmount: num(form.maxAmount), minMonths: num(form.minMonths),
      maxMonths: num(form.maxMonths), annualRate: num(form.annualRate), processingFeePercent: num(form.processingFeePercent),
      activeTo: form.activeTo || null };
    try {
      if (editingId) await adminApi.updateProduct(editingId, payload);
      else await adminApi.createProduct(payload);
      setMessage(editingId ? "Product updated. Existing applications keep their snapshot." : "Product created.");
      setEditingId(null);
      setForm(EMPTY_PRODUCT);
      products.reload();
    } catch (err) {
      setError(err);
    }
  };
  const field = (k, label, type = "text") => (
    <div className="col-md-6"><FormField id={`p-${k}`} label={label}>
      <input {...fieldProps(`p-${k}`, null)} type={type} value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })}
        disabled={k === "code" && Boolean(editingId)} /></FormField></div>
  );

  return (
    <>
      <PageHeader title="Products and policies" />
      {message && <InlineAlert tone="success">{message}</InlineAlert>}
      <InlineAlert error={error} />
      <div className="row g-3">
        <div className="col-xl-7">
          <AsyncContent state={products}>
            <div className="card"><div className="table-responsive"><table className="table align-middle mb-0">
              <caption className="visually-hidden">Products</caption>
              <thead><tr><th scope="col">Code</th><th scope="col">Name</th><th scope="col">Limits</th><th scope="col">Rate</th>
                <th scope="col">Status</th><th scope="col"><span className="visually-hidden">Edit</span></th></tr></thead>
              <tbody>{products.data?.content.map((p) => (
                <tr key={p.id}><td>{p.code}</td><td>{p.name}</td>
                  <td className="small">{formatMoney(p.minAmount)}-{formatMoney(p.maxAmount)} · {p.minMonths}-{p.maxMonths}m</td>
                  <td>{p.annualRate}%</td><td><StatusBadge status={p.availableNow ? "ACTIVE" : "CLOSED"} label={p.availableNow ? "Open" : "Closed"} /></td>
                  <td><button type="button" className="btn btn-sm btn-outline-primary" onClick={() => edit(p)}>Edit</button></td></tr>))}</tbody>
            </table></div></div>
          </AsyncContent>
          <section className="card mt-3"><div className="card-body">
            <h2 className="h6">Eligibility policy versions (immutable)</h2>
            <AsyncContent state={policies}>
              <ul className="small mb-0">{policies.data?.map((p) => (
                <li key={p.policyVersion}>{p.policyVersion}: min income {formatMoney(p.minMonthlyIncome)}, DTI pass ≤ {p.passMaxDti},
                  review ≤ {p.reviewMaxDti}, credit reject &lt; {p.creditRejectBelow}, review &lt; {p.creditReviewBelow} · {formatDate(p.createdAt)}</li>))}</ul>
            </AsyncContent>
          </div></section>
          <section className="card mt-3"><div className="card-body d-flex flex-wrap gap-2 align-items-end">
            <div><label htmlFor="creditMode" className="form-label small mb-1">Synthetic credit provider (demo)</label>
              <select id="creditMode" className="form-select" value={mode} onChange={(e) => setMode(e.target.value)}>
                {["NORMAL", "TIMEOUT", "UNAVAILABLE", "MALFORMED"].map((m) => <option key={m} value={m}>{m}</option>)}</select></div>
            <button type="button" className="btn btn-outline-secondary" onClick={async () => {
              try { await adminApi.setCreditMode(mode); setMessage(`Credit provider simulation: ${mode}`); } catch (err) { setError(err); }
            }}>Apply</button>
          </div></section>
        </div>
        <div className="col-xl-5">
          <form className="card" onSubmit={save}><div className="card-body">
            <h2 className="h6">{editingId ? "Edit product" : "New product"}</h2>
            <div className="row">
              {field("code", "Code")}{field("name", "Name")}{field("purpose", "Purpose")}{field("policyVersion", "Policy version")}
              {field("minAmount", "Min amount", "number")}{field("maxAmount", "Max amount", "number")}
              {field("minMonths", "Min months", "number")}{field("maxMonths", "Max months", "number")}
              {field("annualRate", "Annual rate %", "number")}{field("processingFeePercent", "Processing fee %", "number")}
              {field("activeFrom", "Active from", "date")}{field("activeTo", "Active to", "date")}
            </div>
            <FormField id="p-description" label="Description">
              <textarea {...fieldProps("p-description", null)} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></FormField>
            <div className="form-check"><input id="p-active" type="checkbox" className="form-check-input" checked={form.active}
              onChange={(e) => setForm({ ...form, active: e.target.checked })} /><label htmlFor="p-active" className="form-check-label">Active</label></div>
            <FormActions>
              {editingId && <button type="button" className="btn btn-outline-secondary" onClick={() => { setEditingId(null); setForm(EMPTY_PRODUCT); }}>Cancel</button>}
              <button type="submit" className="btn btn-primary">Save</button>
            </FormActions>
          </div></form>
        </div>
      </div>
    </>
  );
}

export function AuditPage() {
  const [entityId, setEntityId] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const state = useApiState(() => adminApi.audit(query, page), [query, page]);
  return (
    <>
      <PageHeader title="Audit trail" subtitle="Append-only events; sensitive fields are redacted." />
      <form className="d-flex gap-2 mb-3" onSubmit={(e) => { e.preventDefault(); setQuery(entityId.trim()); setPage(0); }}>
        <input className="form-control" aria-label="Entity id" placeholder="Filter by entity id (application, document, account…)"
          value={entityId} onChange={(e) => setEntityId(e.target.value)} />
        <button type="submit" className="btn btn-outline-primary">Search</button>
      </form>
      <AsyncContent state={state} empty={state.data?.content.length === 0} emptyTitle="No events">
        <div className="card"><div className="table-responsive"><table className="table table-sm align-middle mb-0">
          <caption className="visually-hidden">Audit events</caption>
          <thead><tr><th scope="col">Time</th><th scope="col">Action</th><th scope="col">Entity</th><th scope="col">Actor</th>
            <th scope="col">Outcome</th><th scope="col">Correlation</th></tr></thead>
          <tbody>{state.data?.content.map((e) => (
            <tr key={e.id}><td className="small">{formatDateTime(e.timestamp)}</td><td>{e.action}</td>
              <td className="small">{e.entityType} {e.entityId?.slice(0, 8)}</td><td className="small">{e.actorId?.slice(0, 8) ?? "-"}</td>
              <td>{e.outcome}</td><td className="small">{e.correlationId ?? "-"}</td></tr>))}</tbody>
        </table></div></div>
        <Pagination page={page} totalPages={state.data?.totalPages} onChange={setPage} />
      </AsyncContent>
    </>
  );
}
