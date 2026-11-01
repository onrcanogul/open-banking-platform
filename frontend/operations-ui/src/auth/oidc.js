import { CLIENT_ID, IDENTITY_URL } from '../config.js';
import { challengeFor, randomValue } from './pkce.js';

const PENDING_LOGIN = 'pendingLogin';
const SCOPE = 'openid profile';

function redirectUri() {
  return `${window.location.origin}/callback`;
}

/** The page is left for the login, so only what must survive that trip goes to session storage. */
export async function startLogin(returnTo) {
  const verifier = randomValue();
  const state = randomValue();
  sessionStorage.setItem(PENDING_LOGIN, JSON.stringify({ verifier, state, returnTo }));
  const query = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    scope: SCOPE,
    redirect_uri: redirectUri(),
    state,
    code_challenge: await challengeFor(verifier),
    code_challenge_method: 'S256',
  });
  window.location.assign(`${IDENTITY_URL}/oauth2/authorize?${query}`);
}

export async function finishLogin(params) {
  const pending = JSON.parse(sessionStorage.getItem(PENDING_LOGIN) ?? 'null');
  sessionStorage.removeItem(PENDING_LOGIN);
  // A state we did not issue means someone else started this login
  if (!pending || params.get('state') !== pending.state) {
    throw new Error('Giriş isteği doğrulanamadı.');
  }
  if (params.has('error')) {
    throw new Error('Kimlik sunucusu girişi reddetti.');
  }
  const response = await fetch(`${IDENTITY_URL}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: CLIENT_ID,
      redirect_uri: redirectUri(),
      code: params.get('code'),
      code_verifier: pending.verifier,
    }),
  });
  if (!response.ok) {
    throw new Error('Token alınamadı.');
  }
  const tokens = await response.json();
  return { tokens, returnTo: pending.returnTo };
}

export function logoutUrl(idToken) {
  const query = new URLSearchParams({
    id_token_hint: idToken,
    client_id: CLIENT_ID,
    post_logout_redirect_uri: `${window.location.origin}/`,
  });
  return `${IDENTITY_URL}/connect/logout?${query}`;
}

/** For showing the name and roles only. The gateway verifies the signature and decides access. */
export function claimsOf(token) {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  const bytes = Uint8Array.from(atob(payload), (char) => char.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes));
}
