import { createApiClient, normaliseError } from "./client";
import { tokenStore } from "./tokenStore";

/** Fake axios adapter: routes requests to a handler and records them. */
function fakeAdapter(handler) {
  const calls = [];
  const adapter = async (config) => {
    calls.push({ url: config.url, auth: config.headers?.Authorization ?? config.headers?.get?.("Authorization"),
      data: config.data, headers: config.headers });
    const { status, data } = await handler(config, calls);
    const response = { status, data, headers: {}, config, statusText: String(status) };
    if (status >= 400) {
      const error = new Error(`HTTP ${status}`);
      error.config = config;
      error.response = response;
      throw error;
    }
    return response;
  };
  return { adapter, calls };
}

beforeEach(() => tokenStore.clear());

test("attaches the access token to requests", async () => {
  tokenStore.setTokens("access-1", "refresh-1");
  const { adapter, calls } = fakeAdapter(() => ({ status: 200, data: { ok: true } }));
  const api = createApiClient({ adapter });
  await api.get("/api/v1/customers/me");
  expect(calls[0].auth).toBe("Bearer access-1");
});

test("concurrent 401s trigger exactly one refresh and the requests are retried", async () => {
  tokenStore.setTokens("expired", "refresh-1");
  let refreshes = 0;
  const { adapter, calls } = fakeAdapter(async (config) => {
    if (config.url === "/api/v1/auth/refresh") {
      refreshes++;
      await new Promise((r) => setTimeout(r, 10));
      return { status: 200, data: { accessToken: "fresh", refreshToken: "refresh-2", user: { id: "u" } } };
    }
    const auth = config.headers?.Authorization ?? config.headers?.get?.("Authorization");
    return auth === "Bearer fresh" ? { status: 200, data: { url: config.url } } : { status: 401, data: { code: "UNAUTHORIZED" } };
  });
  const api = createApiClient({ adapter });
  const [a, b, c] = await Promise.all([api.get("/api/v1/a"), api.get("/api/v1/b"), api.get("/api/v1/c")]);
  expect(refreshes).toBe(1);
  expect([a.data.url, b.data.url, c.data.url]).toEqual(["/api/v1/a", "/api/v1/b", "/api/v1/c"]);
  expect(tokenStore.getRefreshToken()).toBe("refresh-2");
  expect(calls.filter((x) => x.url === "/api/v1/auth/refresh")).toHaveLength(1);
});

test("failed refresh clears the session and notifies the app", async () => {
  tokenStore.setTokens("expired", "revoked");
  const onSessionExpired = vi.fn();
  const { adapter } = fakeAdapter((config) => (config.url === "/api/v1/auth/refresh"
    ? { status: 401, data: { code: "UNAUTHORIZED", message: "The session is no longer valid." } }
    : { status: 401, data: { code: "UNAUTHORIZED", message: "Authentication is required." } }));
  const api = createApiClient({ adapter, onSessionExpired });
  await expect(api.get("/api/v1/customers/me")).rejects.toMatchObject({ status: 401, code: "UNAUTHORIZED" });
  expect(onSessionExpired).toHaveBeenCalledTimes(1);
  expect(tokenStore.getRefreshToken()).toBeNull();
  expect(tokenStore.getAccessToken()).toBeNull();
});

test("auth endpoints are never retried through refresh", async () => {
  tokenStore.setTokens(null, "refresh-1");
  const { adapter, calls } = fakeAdapter(() => ({ status: 401, data: { code: "UNAUTHORIZED", message: "Invalid email or password." } }));
  const api = createApiClient({ adapter });
  await expect(api.post("/api/v1/auth/login", {})).rejects.toMatchObject({ message: "Invalid email or password." });
  expect(calls).toHaveLength(1);
});

test("normaliseError maps API errors, plain HTTP errors and network failures", () => {
  expect(normaliseError({ response: { status: 409, data: { code: "INVALID_STATE_TRANSITION", message: "Not valid.",
    correlationId: "c-1", fieldErrors: [] } } })).toEqual({ status: 409, code: "INVALID_STATE_TRANSITION", message: "Not valid.",
    correlationId: "c-1", fieldErrors: [] });
  expect(normaliseError({ response: { status: 502, data: "<html>", headers: {} } }).code).toBe("HTTP_502");
  expect(normaliseError(new Error("offline")).code).toBe("NETWORK_ERROR");
});
