import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { claimsOf, finishLogin, logoutUrl, startLogin } from './oidc.js';

const AuthContext = createContext(null);

/**
 * Keeps the session in memory, where a script injected into the page has less to find than in
 * browser storage. A refresh loses it, but the identity server's own session signs the user back in.
 */
export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);

  const login = useCallback((returnTo = '/') => startLogin(returnTo), []);

  const completeLogin = useCallback(async (params) => {
    const { tokens, returnTo } = await finishLogin(params);
    const claims = claimsOf(tokens.access_token);
    setSession({
      accessToken: tokens.access_token,
      idToken: tokens.id_token,
      username: claims.sub,
      roles: claims.roles ?? [],
    });
    return returnTo;
  }, []);

  const logout = useCallback(() => {
    window.location.assign(logoutUrl(session.idToken));
  }, [session]);

  const value = useMemo(() => ({
    session,
    login,
    completeLogin,
    logout,
    hasRole: (role) => session?.roles.includes(role) ?? false,
  }), [session, login, completeLogin, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
