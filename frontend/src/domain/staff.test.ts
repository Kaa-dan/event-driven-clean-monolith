import { describe, expect, it } from 'vitest';
import { initials } from './staff';

describe('initials', () => {
  it('takes first and last word, ignoring a role suffix', () => {
    expect(initials('Maria (manager)')).toBe('MA');
    expect(initials('Ana Beatriz Costa')).toBe('AC');
    expect(initials('  kofi ')).toBe('KO');
    expect(initials('')).toBe('?');
  });
});
