// Paired-device session (API key + identity from GET /me) and the person signed in on it, pure
// (de)serialisation.

import {
  ROLES,
  STAFF_ROLES,
  type Me,
  type Role,
  type RosterEntry,
  type StaffRole,
} from '../api/types';

/** A person signed in on the device (ADR 0017). */
export interface StaffSession {
  token: string;
  /** ISO instant after which the server no longer accepts the token. */
  expiresAt: string;
  staff: RosterEntry;
  /** The device role narrowed by the person's job. */
  effectiveRole: Role;
}

export interface DeviceSession {
  apiKey: string;
  me: Me;
  /** Epoch ms of the last successful GET /me. */
  verifiedAt: number;
  staff?: StaffSession;
}

const isObj = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v);
const isStr = (v: unknown): v is string => typeof v === 'string' && v.length > 0;

export function isRole(v: unknown): v is Role {
  return typeof v === 'string' && (ROLES as readonly string[]).includes(v);
}

export function isStaffRole(v: unknown): v is StaffRole {
  return typeof v === 'string' && (STAFF_ROLES as readonly string[]).includes(v);
}

export function isRosterEntry(v: unknown): v is RosterEntry {
  return (
    isObj(v) &&
    isStr(v.id) &&
    isStr(v.username) &&
    typeof v.displayName === 'string' &&
    isStaffRole(v.role)
  );
}

export function isStaffSession(v: unknown): v is StaffSession {
  return (
    isObj(v) &&
    isStr(v.token) &&
    isStr(v.expiresAt) &&
    isRosterEntry(v.staff) &&
    isRole(v.effectiveRole)
  );
}

/** What the UI may offer: the person's effective role when signed in, else the device role. */
export function effectiveRole(s: DeviceSession): Role {
  return s.staff?.effectiveRole ?? s.me.role;
}

/** Whether the shell must show the staff sign-in screen before anything else. */
export function needsStaffSignIn(s: DeviceSession, nowMs: number): boolean {
  if (s.staff && Date.parse(s.staff.expiresAt) > nowMs) return false;
  return s.me.staffSignInRequired === true;
}

/** Runtime check of a GET /me body (the identity decides what the UI shows). */
export function isMe(v: unknown): v is Me {
  if (!isObj(v) || !isStr(v.terminalId) || typeof v.name !== 'string' || !isRole(v.role))
    return false;
  const b = v.branch;
  return isObj(b) && isStr(b.code) && isStr(b.currency) && isStr(b.zone);
}

export function encodeSession(s: DeviceSession): string {
  return JSON.stringify(s);
}

export function decodeSession(raw: string | null): DeviceSession | null {
  if (!raw) return null;
  try {
    const v: unknown = JSON.parse(raw);
    if (!isObj(v) || !isStr(v.apiKey) || !isMe(v.me)) return null;
    return {
      apiKey: v.apiKey,
      me: v.me,
      verifiedAt: typeof v.verifiedAt === 'number' ? v.verifiedAt : 0,
      ...(isStaffSession(v.staff) ? { staff: v.staff } : {}),
    };
  } catch {
    return null;
  }
}

/** Normalise a typed/pasted key: trims whitespace and an optional "Bearer " prefix. */
export function normaliseApiKey(input: string): string {
  return input
    .trim()
    .replace(/^bearer\s+/i, '')
    .trim();
}
