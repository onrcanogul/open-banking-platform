import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from './AuthContext.jsx';

export default function CallbackPage() {
  const { completeLogin, login } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [failure, setFailure] = useState(null);
  // A code works once, and StrictMode runs effects twice in development
  const started = useRef(false);

  useEffect(() => {
    if (started.current) {
      return;
    }
    started.current = true;
    completeLogin(params)
      .then((returnTo) => navigate(returnTo, { replace: true }))
      .catch((error) => setFailure(error.message));
  }, [completeLogin, navigate, params]);

  if (!failure) {
    return <div className="notice-page">Oturum açılıyor…</div>;
  }
  return (
    <div className="notice-page">
      <h1>Giriş tamamlanamadı</h1>
      <p>{failure}</p>
      <button type="button" className="button button-primary" onClick={() => login()}>Yeniden giriş yap</button>
    </div>
  );
}
