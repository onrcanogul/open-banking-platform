import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext.jsx';

export default function RequireAuth({ children }) {
  const { session, login, hasRole } = useAuth();
  const { pathname } = useLocation();

  useEffect(() => {
    if (!session) {
      login(pathname);
    }
  }, [session, login, pathname]);

  if (!session) {
    return <div className="notice-page">Giriş sayfasına yönlendiriliyorsunuz…</div>;
  }
  if (!hasRole('VIEWER')) {
    return (
      <div className="notice-page">
        <h1>Yetkiniz bulunmuyor</h1>
        <p>Bu panel için görüntüleme yetkisi gereklidir.</p>
      </div>
    );
  }
  return children;
}
