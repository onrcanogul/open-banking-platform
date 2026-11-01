import { describe, expect, it } from 'vitest';
import { formatAmount, formatDate } from './format.js';

describe('format', () => {
  it('writes amounts the Turkish way', () => {
    expect(formatAmount(1500.5)).toBe('1.500,50');
  });

  it('shows the time in Istanbul whatever the machine zone is', () => {
    expect(formatDate('2026-11-01T09:30:00Z')).toContain('12:30:00');
  });
});
