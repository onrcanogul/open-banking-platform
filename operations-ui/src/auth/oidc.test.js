import { describe, expect, it } from 'vitest';
import { claimsOf } from './oidc.js';

function tokenWith(claims) {
  const payload = Buffer.from(JSON.stringify(claims)).toString('base64url');
  return `header.${payload}.signature`;
}

describe('claimsOf', () => {
  it('reads the name and roles', () => {
    const claims = claimsOf(tokenWith({ sub: 'viewer', roles: ['VIEWER'] }));

    expect(claims.sub).toBe('viewer');
    expect(claims.roles).toEqual(['VIEWER']);
  });

  it('keeps Turkish characters intact', () => {
    expect(claimsOf(tokenWith({ sub: 'Çiğdem Şahin' })).sub).toBe('Çiğdem Şahin');
  });
});
