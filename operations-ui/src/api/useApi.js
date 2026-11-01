import { useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { API_URL } from '../config.js';

const MESSAGES = {
  403: 'Bu işlem için yetkiniz bulunmuyor.',
  404: 'Kayıt bulunamadı.',
};

export function useApi() {
  const { session, login } = useAuth();
  const { pathname } = useLocation();

  return useCallback(async (path, options = {}) => {
    let response;
    try {
      response = await fetch(`${API_URL}${path}`, {
        ...options,
        headers: { Authorization: `Bearer ${session.accessToken}` },
      });
    } catch {
      throw new Error('Sunucuya ulaşılamıyor.');
    }
    if (response.status === 401) {
      // The token expired. While the identity server's session lives, this needs no password
      await login(pathname);
      throw new Error('Oturum yenileniyor…');
    }
    if (!response.ok) {
      throw new Error(MESSAGES[response.status] ?? 'İşlem tamamlanamadı. Lütfen tekrar deneyin.');
    }
    return response.json();
  }, [session, login, pathname]);
}
