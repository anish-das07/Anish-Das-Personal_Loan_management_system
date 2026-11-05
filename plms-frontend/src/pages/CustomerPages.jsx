import { useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { PageHeader } from "../layouts/AppLayout";
import { AsyncContent, InlineAlert, Pagination, SummaryCard } from "../components/common";
import { FormActions, FormField, CurrencyInput, fieldProps, fieldErrorMap, isValidMoney } from "../components/forms";
import { EMIDueCard, LoanApplicationTable, LoanStatusTimeline } from "../components/loan";
import { EMICalculator } from "../components/loan/EMICalculator";
import { useApiState } from "../hooks/useApiState";
import { applicationApi, customerApi, productApi } from "../features/applications/applicationApi";
import { accountApi } from "../features/staff/staffApi";
import { formatDate, formatMoney, normaliseMoneyInput } from "../utils/format";

export function CustomerDashboard() {
  const navigate = useNavigate();
  const state = useApiState(async () => {
    const [profile, applications, accounts] = await Promise.all([customerApi.me(), applicationApi.mine(),
      accountApi.mine()]);
    const active = accounts.find((a) => a.status === "ACTIVE") ?? null;
    const schedule = active ? await accountApi.schedule(active.loanAccountId) : null;
    return { profile, applications, accounts, active, next: schedule?.installments.find((i) => i.status !== "PAID") ?? null };
  }, []);
  const d = state.data;
  return (
    <>
      <PageHeader title={d ? `Hello, ${d.profile.fullName.split(" ")[0]}` : "Dashboard"}
        actions={d && d.profile.completeness < 100 && (
          <Link to="/app/profile" className="btn btn-outline-primary">Complete profile {d.profile.completeness}%</Link>)} />
      <AsyncContent state={state}>
        {d && (
          <>
            <div className="row g-3 mb-3">
              <div className="col-6 col-lg-3"><SummaryCard label="Applications" value={d.applications.length} /></div>
              <div className="col-6 col-lg-3"><SummaryCard label="In review"
                value={d.applications.filter((a) => ["SUBMITTED", "UNDER_REVIEW"].includes(a.status)).length} /></div>
              <div className="col-6 col-lg-3"><SummaryCard label="Active loans"
                value={d.accounts.filter((a) => a.status === "ACTIVE").length} /></div>
              <div className="col-6 col-lg-3"><SummaryCard label="Next EMI" value={d.next ? formatDate(d.next.dueDate) : "-"}
                hint={d.next ? formatMoney(d.next.total) : undefined} /></div>
            </div>
            {d.applications[0] && (
              <section className="card mb-3" aria-labelledby="latest-app">
                <div className="card-body">
                  <h2 id="latest-app" className="h6">Latest application · {d.applications[0].reference}</h2>
                  <LoanStatusTimeline status={d.applications[0].status} />
                  <p className="small mb-0">{d.applications[0].nextAction}</p>
                </div>
              </section>
            )}
            <div className="row g-3">
              <div className="col-lg-8">
                <section className="card h-100"><div className="card-body">
                  <h2 className="h6">My applications</h2>
                  <LoanApplicationTable rows={d.applications} onOpen={(id) => navigate(`/app/applications/${id}`)} />
                </div></section>
              </div>
              <div className="col-lg-4"><EMIDueCard account={d.active} nextInstallment={d.next} /></div>
            </div>
          </>
        )}
      </AsyncContent>
    </>
  );
}

const EMPTY_PROFILE = { fullName: "", dateOfBirth: "", phone: "", addressLine1: "", city: "", state: "", postalCode: "",
  employmentType: "SALARIED", employerName: "", employmentStartDate: "", occupation: "", monthlyIncome: "",
  existingMonthlyObligations: "" };

export function ProfilePage() {
  const location = useLocation();
  const [form, setForm] = useState(null);
  const [errors, setErrors] = useState({});
  const [saved, setSaved] = useState(false);
  const [apiError, setApiError] = useState(null);
  const [saving, setSaving] = useState(false);
  const state = useApiState(async () => {
    const p = await customerApi.me();
    const f = { ...EMPTY_PROFILE };
    Object.keys(f).forEach((k) => { if (p[k] !== null && p[k] !== undefined) f[k] = String(p[k]); });
    setForm(f);
    return p;
  }, []);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const setValue = (k) => (v) => setForm((f) => ({ ...f, [k]: v }));

  const validate = () => {
    const e = {};
    if (!form.fullName.trim()) e.fullName = "Full name is required";
    if (!form.dateOfBirth) e.dateOfBirth = "Date of birth is required";
    if (!/^[0-9+][0-9 -]{6,19}$/.test(form.phone)) e.phone = "Enter a valid phone number";
    if (!form.addressLine1.trim()) e.addressLine1 = "Address is required";
    if (!form.city.trim()) e.city = "City is required";
    if (!/^[A-Za-z0-9 -]{3,12}$/.test(form.postalCode)) e.postalCode = "Enter a valid postal code";
    if (form.monthlyIncome && !isValidMoney(form.monthlyIncome)) e.monthlyIncome = "Monthly income must be positive";
    if (form.existingMonthlyObligations && !isValidMoney(form.existingMonthlyObligations, { allowZero: true })) {
      e.existingMonthlyObligations = "Enter a valid amount";
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const save = async (event) => {
    event.preventDefault();
    setSaved(false);
    if (!validate()) return;
    setSaving(true);
    setApiError(null);
    try {
      const money = new Set(["monthlyIncome", "existingMonthlyObligations"]);
      const payload = Object.fromEntries(Object.entries(form).map(([k, v]) => [k,
        v === "" ? null : money.has(k) ? Number(normaliseMoneyInput(v)) : v]));
      await customerApi.update(payload);
      setSaved(true);
      state.reload();
    } catch (err) {
      setApiError(err);
      setErrors(fieldErrorMap(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader title="My profile" subtitle="Personal, contact, employment and income details." />
      {location.state?.welcome && <InlineAlert tone="info">Welcome! Complete your profile before applying.</InlineAlert>}
      <AsyncContent state={state}>
        {form && state.data && (
          <form onSubmit={save} noValidate className="card"><div className="card-body">
            <div className="progress mb-3" role="progressbar" aria-label="Profile completeness"
              aria-valuenow={state.data.completeness} aria-valuemin="0" aria-valuemax="100">
              <div className="progress-bar" style={{ width: `${state.data.completeness}%` }}>{state.data.completeness}%</div>
            </div>
            {saved && <InlineAlert tone="success">Profile saved.</InlineAlert>}
            <InlineAlert error={apiError} />
            <fieldset className="mb-2"><legend className="h6">Personal</legend>
              <div className="row">
                <div className="col-md-6"><FormField id="fullName" label="Full name" error={errors.fullName} required>
                  <input {...fieldProps("fullName", errors.fullName)} value={form.fullName} onChange={set("fullName")} /></FormField></div>
                <div className="col-md-3"><FormField id="dateOfBirth" label="Date of birth" error={errors.dateOfBirth} required>
                  <input {...fieldProps("dateOfBirth", errors.dateOfBirth)} type="date" value={form.dateOfBirth} onChange={set("dateOfBirth")} /></FormField></div>
                <div className="col-md-3"><FormField id="phone" label="Phone" error={errors.phone} required>
                  <input {...fieldProps("phone", errors.phone)} type="tel" value={form.phone} onChange={set("phone")} /></FormField></div>
              </div>
            </fieldset>
            <fieldset className="mb-2"><legend className="h6">Address</legend>
              <div className="row">
                <div className="col-md-6"><FormField id="addressLine1" label="Address line 1" error={errors.addressLine1} required>
                  <input {...fieldProps("addressLine1", errors.addressLine1)} value={form.addressLine1} onChange={set("addressLine1")} /></FormField></div>
                <div className="col-md-2"><FormField id="city" label="City" error={errors.city} required>
                  <input {...fieldProps("city", errors.city)} value={form.city} onChange={set("city")} /></FormField></div>
                <div className="col-md-2"><FormField id="state" label="State">
                  <input {...fieldProps("state", null)} value={form.state} onChange={set("state")} /></FormField></div>
                <div className="col-md-2"><FormField id="postalCode" label="Postal code" error={errors.postalCode} required>
                  <input {...fieldProps("postalCode", errors.postalCode)} value={form.postalCode} onChange={set("postalCode")} /></FormField></div>
              </div>
            </fieldset>
            <fieldset className="mb-2"><legend className="h6">Employment and income</legend>
              <div className="row">
                <div className="col-md-3"><FormField id="employmentType" label="Employment type">
                  <select {...fieldProps("employmentType", null, { className: "form-select" })} value={form.employmentType}
                    onChange={set("employmentType")}>
                    <option value="SALARIED">Salaried</option><option value="SELF_EMPLOYED">Self-employed</option>
                    <option value="OTHER">Other</option></select></FormField></div>
                <div className="col-md-3"><FormField id="employerName" label="Employer">
                  <input {...fieldProps("employerName", null)} value={form.employerName} onChange={set("employerName")} /></FormField></div>
                <div className="col-md-3"><FormField id="employmentStartDate" label="Start date">
                  <input {...fieldProps("employmentStartDate", null)} type="date" value={form.employmentStartDate}
                    onChange={set("employmentStartDate")} /></FormField></div>
                <div className="col-md-3"><FormField id="occupation" label="Occupation">
                  <input {...fieldProps("occupation", null)} value={form.occupation} onChange={set("occupation")} /></FormField></div>
                <div className="col-md-6"><FormField id="monthlyIncome" label="Monthly income" error={errors.monthlyIncome}>
                  <CurrencyInput id="monthlyIncome" value={form.monthlyIncome} onChange={setValue("monthlyIncome")} error={errors.monthlyIncome} /></FormField></div>
                <div className="col-md-6"><FormField id="existingMonthlyObligations" label="Existing monthly obligations"
                  error={errors.existingMonthlyObligations}>
                  <CurrencyInput id="existingMonthlyObligations" value={form.existingMonthlyObligations}
                    onChange={setValue("existingMonthlyObligations")} error={errors.existingMonthlyObligations} /></FormField></div>
              </div>
            </fieldset>
            <FormActions><button type="submit" className="btn btn-primary" disabled={saving}>{saving ? "Saving…" : "Save changes"}</button></FormActions>
          </div></form>
        )}
      </AsyncContent>
    </>
  );
}

export function ProductCataloguePage() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const state = useApiState(() => productApi.list({ q: q || undefined, page }), [q, page]);
  return (
    <>
      <PageHeader title="Loan products" actions={
        <input type="search" className="form-control" placeholder="Search products…" aria-label="Search products"
          value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />} />
      <AsyncContent state={state} empty={state.data?.content.length === 0} emptyTitle="No matching products">
        <div className="row g-3">
          {state.data?.content.map((p) => (
            <div key={p.id} className="col-sm-6 col-lg-4">
              <article className="card h-100">
                <div className="card-body d-flex flex-column">
                  <h2 className="h5">{p.name}</h2>
                  <p className="small text-body-secondary">{p.description}</p>
                  <p className="mb-1">Example rate*: <strong>{p.annualRate}% p.a.</strong></p>
                  <p className="small mb-3">{formatMoney(p.minAmount)} - {formatMoney(p.maxAmount)} · {p.minMonths}-{p.maxMonths} months</p>
                  <Link to={`/app/products/${p.id}`} className="btn btn-outline-primary mt-auto">View details</Link>
                </div>
              </article>
            </div>
          ))}
        </div>
        <Pagination page={page} totalPages={state.data?.totalPages} onChange={setPage} />
        <p className="small text-body-secondary mt-3">*Illustrative terms; final terms subject to approved policy and review.</p>
      </AsyncContent>
    </>
  );
}

export function ProductDetailsPage() {
  const { productId } = useParams();
  const state = useApiState(() => productApi.get(productId), [productId]);
  const p = state.data;
  return (
    <AsyncContent state={state}>
      {p && (
        <>
          <PageHeader title={p.name} subtitle={p.description}
            actions={p.availableNow && <Link className="btn btn-primary" to={`/app/applications/new?productId=${p.id}`}>Apply for this loan</Link>} />
          <div className="row g-3 mb-4">
            <div className="col-md-3"><SummaryCard label="Amount" value={`${formatMoney(p.minAmount)} - ${formatMoney(p.maxAmount)}`} /></div>
            <div className="col-md-3"><SummaryCard label="Tenure" value={`${p.minMonths}-${p.maxMonths} months`} /></div>
            <div className="col-md-3"><SummaryCard label="Rate (fixed, reducing balance)" value={`${p.annualRate}% p.a.`} /></div>
            <div className="col-md-3"><SummaryCard label="Processing fee" value={`${p.processingFeePercent}%`} hint="Deducted at disbursement" /></div>
          </div>
          <section className="card mb-3"><div className="card-body">
            <h2 className="h6">Eligibility and documents</h2>
            <p className="small mb-1">Eligibility is assessed against policy {p.policyVersion} (income, affordability, employment, age and a synthetic credit assessment).</p>
            <p className="small mb-0">Documents needed: identity proof and income proof (bank statement optional).</p>
          </div></section>
          <div className="card"><div className="card-body">
            <EMICalculator product={p} initialPrincipal={Math.min(Math.max(Number(p.minAmount), 300000), Number(p.maxAmount)).toFixed(2)}
              initialMonths={Math.min(Math.max(36, p.minMonths), p.maxMonths)} quote={productApi.quote} />
          </div></div>
        </>
      )}
    </AsyncContent>
  );
}
