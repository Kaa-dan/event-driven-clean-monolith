import { createContext, useContext } from 'react';
import type { Me, Role, RosterEntry } from '../api/types';

export interface SessionValue {
  me: Me;
  /** The person signed in on the device, if any. */
  staff: RosterEntry | null;
  /** What the UI may offer: the person's effective role, or the device role when nobody is signed in. */
  role: Role;
  signOut: () => void;
}

export const SessionContext = createContext<SessionValue | null>(null);

/** The paired device's identity. Only valid below the signed-in shell. */
export function useSession(): SessionValue {
  const v = useContext(SessionContext);
  if (!v) throw new Error('useSession() used outside of a signed-in session');
  return v;
}

/** Same as useSession but returns null outside a session (shared components, tests). */
export function useOptionalSession(): SessionValue | null {
  return useContext(SessionContext);
}
