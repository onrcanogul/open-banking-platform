import { describe, expect, it } from 'vitest';
import { challengeFor, randomValue } from './pkce.js';

describe('pkce', () => {
  it('matches the example in the PKCE standard', async () => {
    const challenge = await challengeFor('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk');

    expect(challenge).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });

  it('creates a different url safe value each time', () => {
    const first = randomValue();

    expect(first).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(randomValue()).not.toBe(first);
  });
});
