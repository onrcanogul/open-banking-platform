function base64Url(bytes) {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function randomValue() {
  return base64Url(crypto.getRandomValues(new Uint8Array(32)));
}

/** The server sees this hash first and the verifier only at the end, so a stolen code is useless. */
export async function challengeFor(verifier) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}
