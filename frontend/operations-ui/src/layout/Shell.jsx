import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

const ROLE_NAMES = { VIEWER: 'Görüntüleme', RECONCILER: 'Mutabakat' };

export default function Shell() {
  const { session, logout } = useAuth();

  return (
    <div className="shell">
      <header className="topbar">
        <span className="topbar-title">Operasyon Paneli</span>
        <span className="topbar-env">TEST ORTAMI</span>
        <div className="topbar-user">
          <span>
            <strong>{session.username}</strong>
            <small>{session.roles.map((role) => ROLE_NAMES[role] ?? role).join(', ')}</small>
          </span>
          <button type="button" className="button button-topbar" onClick={logout}>Güvenli Çıkış</button>
        </div>
      </header>
      <nav className="sidebar" aria-label="Ana menü">
        <h2>Kredi Kullandırım</h2>
        <NavLink to="/disbursements">Kullandırımlar</NavLink>
        <h2>Mutabakat</h2>
        <NavLink to="/findings">Bulgular</NavLink>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
