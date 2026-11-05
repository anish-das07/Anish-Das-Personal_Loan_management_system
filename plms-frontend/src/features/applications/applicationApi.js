import { api } from "../../api/client";

export const customerApi = {
  me: () => api.get("/api/v1/customers/me").then((r) => r.data),
  update: (payload) => api.put("/api/v1/customers/me", payload).then((r) => r.data)
};

export const productApi = {
  list: (params = {}) => api.get("/api/v1/loan-products", { params: { size: 12, ...params } }).then((r) => r.data),
  get: (id) => api.get(`/api/v1/loan-products/${id}`).then((r) => r.data),
  quote: (payload) => api.post("/api/v1/emi/quote", payload).then((r) => r.data)
};

export const applicationApi = {
  mine: () => api.get("/api/v1/applications").then((r) => r.data),
  get: (id) => api.get(`/api/v1/applications/${id}`).then((r) => r.data),
  history: (id) => api.get(`/api/v1/applications/${id}/history`).then((r) => r.data),
  create: (payload, idempotencyKey) =>
    api.post("/api/v1/applications", payload, { headers: { "Idempotency-Key": idempotencyKey } }).then((r) => r.data),
  update: (id, payload) => api.patch(`/api/v1/applications/${id}`, payload).then((r) => r.data),
  submit: (id, idempotencyKey) =>
    api.post(`/api/v1/applications/${id}/submit`, { declarationAccepted: true },
      { headers: { "Idempotency-Key": idempotencyKey } }).then((r) => r.data),
  withdraw: (id) => api.post(`/api/v1/applications/${id}/withdraw`).then((r) => r.data),
  eligibility: (id) => api.get(`/api/v1/applications/${id}/eligibility`).then((r) => r.data),
  credit: (id) => api.get("/api/v1/credit-assessments", { params: { applicationId: id } }).then((r) => r.data)
};

export const documentApi = {
  list: (appId) => api.get(`/api/v1/applications/${appId}/documents`).then((r) => r.data),
  checklist: (appId) => api.get(`/api/v1/applications/${appId}/documents/checklist`).then((r) => r.data),
  upload: (appId, category, file, onUploadProgress) => {
    const form = new FormData();
    form.append("applicationId", appId);
    form.append("category", category);
    form.append("file", file);
    return api.post("/api/v1/documents", form, { headers: { "Content-Type": "multipart/form-data" }, onUploadProgress, timeout: 60000 })
      .then((r) => r.data);
  },
  decide: (id, payload) => api.post(`/api/v1/documents/${id}/decision`, payload).then((r) => r.data),
  queue: (status = "PENDING_REVIEW", page = 0) =>
    api.get("/api/v1/staff/documents", { params: { status, page, size: 20 } }).then((r) => r.data),
  verifications: (id) => api.get(`/api/v1/documents/${id}/verifications`).then((r) => r.data),
  /** Authorised download through the API (never a public URL); returns an object URL for preview. */
  contentUrl: async (id) => {
    const r = await api.get(`/api/v1/documents/${id}/content`, { responseType: "blob" });
    return URL.createObjectURL(r.data);
  }
};
