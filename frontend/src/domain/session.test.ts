import { describe, expect, it } from 'vitest';
import {
  decodeSession,
  effectiveRole,
  encodeSession,
  isMe,
  needsStaffSignIn,
  normaliseApiKey,
  type DeviceSession,
  type StaffSession,
} from './session';

const me = {
  terminalId: 'pos-1',
  name: 'Front counter',
  role: 'POS' as const,
  branch: { code: 'HOSP01', currency: 'USD', zone: 'America/New_York' },
};

const staff: StaffSession = {
  token: 'ss_x',
  expiresAt: '2026-10-22T20:00:00Z',
  staff: { id: 's1', username: 'carl', displayName: 'Carl', role: 'CASHIER' },
  effectiveRole: 'POS',
};

describe('staff session', () => {
  it('round-trips with the person, and drops a malformed one', () => {
    const s: DeviceSession = { apiKey: 'sf_x', me, verifiedAt: 5, staff };
    expect(decodeSession(encodeSession(s))).toEqual(s);
    const bad = { apiKey: 'sf_x', me, verifiedAt: 5, staff: { ...staff, effectiveRole: 'ROOT' } };
    expect(decodeSession(JSON.stringify(bad))).toEqual({ apiKey: 'sf_x', me, verifiedAt: 5 });
  });

  it('acts with the narrowed role of the signed-in person', () => {
    const managerDevice = { ...me, role: 'MANAGER' as const };
    expect(effectiveRole({ apiKey: 'k', me: managerDevice, verifiedAt: 0 })).toBe('MANAGER');
    expect(effectiveRole({ apiKey: 'k', me: managerDevice, verifiedAt: 0, staff })).toBe('POS');
  });

  it('requires signing in only when the node says so and nobody valid is signed in', () => {
    const now = Date.parse('2026-10-22T12:00:00Z');
    const required = { ...me, staffSignInRequired: true };
    expect(needsStaffSignIn({ apiKey: 'k', me, verifiedAt: 0 }, now)).toBe(false);
    expect(needsStaffSignIn({ apiKey: 'k', me: required, verifiedAt: 0 }, now)).toBe(true);
    expect(needsStaffSignIn({ apiKey: 'k', me: required, verifiedAt: 0, staff }, now)).toBe(false);
    const later = Date.parse('2026-10-22T21:00:00Z');
    expect(needsStaffSignIn({ apiKey: 'k', me: required, verifiedAt: 0, staff }, later)).toBe(true);
  });
});

describe('session', () => {
  it('round-trips', () => {
    const s: DeviceSession = { apiKey: 'sf_x', me, verifiedAt: 5 };
    expect(decodeSession(encodeSession(s))).toEqual(s);
  });

  it('rejects malformed values', () => {
    expect(decodeSession(null)).toBeNull();
    expect(decodeSession('{')).toBeNull();
    expect(decodeSession(JSON.stringify({ apiKey: '', me }))).toBeNull();
    expect(decodeSession(JSON.stringify({ apiKey: 'k', me: { ...me, role: 'ADMIN' } }))).toBeNull();
    expect(decodeSession(JSON.stringify({ apiKey: 'k', me: { ...me, branch: null } }))).toBeNull();
    expect(decodeSession(JSON.stringify({ apiKey: 'k', me }))).toEqual({
      apiKey: 'k',
      me,
      verifiedAt: 0,
    });
  });

  it('validates /me bodies', () => {
    expect(isMe(me)).toBe(true);
    expect(isMe({ ...me, terminalId: '' })).toBe(false);
    expect(isMe('x')).toBe(false);
  });

  it('normalises pasted keys', () => {
    expect(normaliseApiKey('  sf_demo_pos \n')).toBe('sf_demo_pos');
    expect(normaliseApiKey('Bearer sf_demo_pos')).toBe('sf_demo_pos');
  });
});
