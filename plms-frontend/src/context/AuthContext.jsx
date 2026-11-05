import { createContext, useCallback, useEffect, useMemo, useState } from "react";
import { api, setSessionExpiredHandler } from "../api/client";
import { tokenStore } from "../api/tokenStore";
import { authApi } from "../features/auth/authApi";

export const AuthContext = createContext(null);

/**
 * Session state. Roles shown in the UI come from the server's login/refresh response, never from
 * client input. Route guards and menus use them for usability only; the API enforces access.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [initialising, setInitialising] = useState(true);

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));
    let cancelled = false;
    (async () => {
      if (tokenStore.getRefreshToken()) {
        try {
          const data = await api.refreshSession();
          if (!cancelled) setUser(data.user);
        } catch {
          tokenStore.clear();
        }
      }
      if (!cancelled) setInitialising(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email, password) => {
    const data = await authApi.login(email.trim(), password);
    tokenStore.setTokens(data.accessToken, data.refreshToken);
    setUser(data.user);
    return data.user;
  }, []);

  const logout = useCallback(async () => {
    const refresh = tokenStore.getRefreshToken();
    try {
      if (refresh) await authApi.logout(refresh);
    } catch {
      // Logging out locally still succeeds; the server session expires on its own.
    } finally {
      tokenStore.clear();
      setUser(null);
    }
  }, []);

  const value = useMemo(() => ({
    user,
    initialising,
    login,
    logout,
    hasRole: (...roles) => Boolean(user?.roles?.some((r) => roles.includes(r)))
  }), [user, initialising, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
