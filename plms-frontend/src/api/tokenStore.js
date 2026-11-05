/**
 * Token storage strategy (documented trade-off, case study section 10):
 * - The short-lived access token lives only in memory, so it is never persisted.
 * - The refresh token is kept in sessionStorage so a page reload in the same tab can resume the session;
 *   it is cleared when the tab closes. It is rotated on every refresh and revoked on logout.
 * A stronger production option is an HttpOnly, Secure, SameSite cookie for the refresh token plus CSRF
 * protection on the refresh endpoint.
 */
const REFRESH_KEY = "plms.refresh";
let accessToken = null;

function storage() {
  try {
    return globalThis.sessionStorage ?? null;
  } catch {
    return null;
  }
}

export const tokenStore = {
  getAccessToken: () => accessToken,
  getRefreshToken: () => storage()?.getItem(REFRESH_KEY) ?? null,
  setTokens(access, refresh) {
    accessToken = access ?? null;
    const s = storage();
    if (!s) return;
    if (refresh) s.setItem(REFRESH_KEY, refresh);
    else s.removeItem(REFRESH_KEY);
  },
  clear() {
    accessToken = null;
    storage()?.removeItem(REFRESH_KEY);
  }
};
