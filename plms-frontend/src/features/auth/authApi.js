import { api } from "../../api/client";

export const authApi = {
  login: (email, password) => api.post("/api/v1/auth/login", { email, password }).then((r) => r.data),
  register: (payload) => api.post("/api/v1/auth/register", payload).then((r) => r.data),
  logout: (refreshToken) => api.post("/api/v1/auth/logout", { refreshToken }),
  requestReset: (email) => api.post("/api/v1/auth/password-reset/request", { email }).then((r) => r.data),
  confirmReset: (token, newPassword) =>
    api.post("/api/v1/auth/password-reset/confirm", { token, newPassword }).then((r) => r.data)
};

export const adminApi = {
  users: () => api.get("/api/v1/admin/users").then((r) => r.data),
  createStaff: (payload) => api.post("/api/v1/admin/users", payload).then((r) => r.data),
  updateUser: (id, payload) => api.put(`/api/v1/admin/users/${id}`, payload).then((r) => r.data),
  products: () => api.get("/api/v1/admin/products?size=100").then((r) => r.data),
  createProduct: (payload) => api.post("/api/v1/admin/products", payload).then((r) => r.data),
  updateProduct: (id, payload) => api.put(`/api/v1/admin/products/${id}`, payload).then((r) => r.data),
  audit: (entityId, page = 0) =>
    api.get("/api/v1/audit-events", { params: { entityId: entityId || undefined, page, size: 25 } }).then((r) => r.data),
  setCreditMode: (mode) => api.put("/api/v1/admin/simulations/credit-provider", { mode }).then((r) => r.data),
  policies: () => api.get("/api/v1/admin/eligibility-policies").then((r) => r.data)
};
