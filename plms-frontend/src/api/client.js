import axios from "axios";
import { tokenStore } from "./tokenStore";

/** Normalised error shape used by every page: never exposes raw stack traces or headers. */
export function normaliseError(error) {
  const response = error?.response;
  const data = response?.data;
  if (data && typeof data === "object" && data.code) {
    return {
      status: response.status,
      code: data.code,
      message: data.message || "Something went wrong.",
      correlationId: data.correlationId ?? null,
      fieldErrors: Array.isArray(data.fieldErrors) ? data.fieldErrors : []
    };
  }
  if (response) {
    return { status: response.status, code: "HTTP_" + response.status, message: "The request could not be completed.",
      correlationId: response.headers?.["x-correlation-id"] ?? null, fieldErrors: [] };
  }
  return { status: 0, code: "NETWORK_ERROR", message: "The service is unreachable. Check your connection and try again.",
    correlationId: null, fieldErrors: [] };
}

/**
 * Creates the API client. On a 401 it performs a single coordinated refresh: concurrent requests that
 * fail together wait for the same refresh promise, so only one request rotates the refresh token.
 * Authorization headers are never logged.
 */
export function createApiClient({ baseURL = import.meta.env?.VITE_API_BASE_URL ?? "", adapter, onSessionExpired } = {}) {
  const api = axios.create({ baseURL, timeout: 10000, headers: { "Content-Type": "application/json" }, adapter });
  let refreshPromise = null;

  api.interceptors.request.use((config) => {
    const token = tokenStore.getAccessToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  });

  async function refreshOnce() {
    if (!refreshPromise) {
      const refreshToken = tokenStore.getRefreshToken();
      refreshPromise = (async () => {
        if (!refreshToken) throw new Error("No refresh token");
        const { data } = await api.post("/api/v1/auth/refresh", { refreshToken }, { _skipAuthRetry: true });
        tokenStore.setTokens(data.accessToken, data.refreshToken);
        return data;
      })().finally(() => {
        refreshPromise = null;
      });
    }
    return refreshPromise;
  }

  api.interceptors.response.use(
    (response) => response,
    async (error) => {
      const original = error.config;
      const status = error.response?.status;
      const isAuthCall = original?.url?.includes("/api/v1/auth/");
      if (status === 401 && original && !original._retried && !original._skipAuthRetry && !isAuthCall) {
        original._retried = true;
        try {
          await refreshOnce();
          original.headers = { ...(original.headers || {}), Authorization: `Bearer ${tokenStore.getAccessToken()}` };
          return api(original);
        } catch {
          tokenStore.clear();
          onSessionExpired?.();
        }
      }
      return Promise.reject(normaliseError(error));
    }
  );

  api.refreshSession = refreshOnce;
  return api;
}

let sessionExpiredHandler = null;
export function setSessionExpiredHandler(handler) {
  sessionExpiredHandler = handler;
}

/** Shared application client. */
export const api = createApiClient({ onSessionExpired: () => sessionExpiredHandler?.() });
