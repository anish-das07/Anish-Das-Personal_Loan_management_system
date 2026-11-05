import { api } from "../../api/client";

export const staffApi = {
  dashboard: () => api.get("/api/v1/staff/dashboard").then((r) => r.data),
  queue: (queue = "REVIEW", page = 0) =>
    api.get("/api/v1/staff/applications", { params: { queue, page, size: 20 } }).then((r) => r.data),
  review: (id) => api.get(`/api/v1/staff/applications/${id}/review`).then((r) => r.data),
  assign: (id) => api.post(`/api/v1/applications/${id}/assign`).then((r) => r.data),
  recommend: (id, payload) => api.post(`/api/v1/applications/${id}/recommendation`, payload).then((r) => r.data),
  decide: (id, payload) => api.post(`/api/v1/applications/${id}/decision`, payload).then((r) => r.data),
  evaluate: (applicationId, policyVersion) =>
    api.post("/api/v1/eligibility/evaluations", { applicationId, policyVersion }).then((r) => r.data),
  creditAssess: (applicationId) => api.post("/api/v1/credit-assessments", { applicationId }).then((r) => r.data),
  portfolio: () => api.get("/api/v1/reports/portfolio").then((r) => r.data)
};

export const financeApi = {
  eligible: () => api.get("/api/v1/finance/disbursements/eligible").then((r) => r.data),
  disburse: (accountId, payload, idempotencyKey) =>
    api.post(`/api/v1/loan-accounts/${accountId}/disbursements`, payload,
      { headers: { "Idempotency-Key": idempotencyKey } }).then((r) => r.data),
  disbursements: (accountId) => api.get(`/api/v1/loan-accounts/${accountId}/disbursements`).then((r) => r.data),
  reconcile: (id) => api.post(`/api/v1/disbursements/${id}/reconcile`).then((r) => r.data),
  overdue: (asOf, page = 0) => api.get("/api/v1/reports/overdue", { params: { asOf: asOf || undefined, page, size: 20 } })
    .then((r) => r.data),
  closureCheck: (accountId) => api.get(`/api/v1/loan-accounts/${accountId}/closure-check`).then((r) => r.data),
  close: (accountId) => api.post(`/api/v1/loan-accounts/${accountId}/closure`).then((r) => r.data)
};

export const accountApi = {
  mine: () => api.get("/api/v1/loan-accounts/mine").then((r) => r.data),
  get: (id) => api.get(`/api/v1/loan-accounts/${id}`).then((r) => r.data),
  schedule: (id) => api.get(`/api/v1/loan-accounts/${id}/schedule`).then((r) => r.data),
  repayments: (id) => api.get(`/api/v1/loan-accounts/${id}/repayments`).then((r) => r.data),
  pay: (payload, idempotencyKey) =>
    api.post("/api/v1/repayments", payload, { headers: { "Idempotency-Key": idempotencyKey } }).then((r) => r.data),
  reconcile: (id) => api.post(`/api/v1/repayments/${id}/reconcile`).then((r) => r.data),
  receipt: (id) => api.get(`/api/v1/repayments/${id}/receipt`).then((r) => r.data)
};

export const notificationApi = {
  mine: () => api.get("/api/v1/notifications").then((r) => r.data),
  read: (id) => api.post(`/api/v1/notifications/${id}/read`)
};
