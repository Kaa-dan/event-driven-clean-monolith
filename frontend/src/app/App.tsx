import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { onStaffUnauthorized, onUnauthorized, setApiKey, setStaffToken } from '../api/client';
import { getMe, staffSignOut } from '../api/endpoints';
import { appsForRole, formatRoute, resolveRoute, type AppId, type Route } from '../domain/routes';
import {
  decodeSession,
  effectiveRole,
  encodeSession,
  isMe,
  needsStaffSignIn,
  type DeviceSession,
  type StaffSession,
} from '../domain/session';
import { DemoConnect } from '../features/auth/DemoConnect';
import { SignIn } from '../features/auth/SignIn';
import { initials } from '../domain/staff';
import { StaffSignIn } from '../features/auth/StaffSignIn';
import { BackOffice } from '../features/backoffice/BackOffice';
import { go } from '../features/common/nav';
import { DevTools } from '../features/devtools/DevTools';
import { KitchenDisplay } from '../features/kitchen/KitchenDisplay';
import { PosApp } from '../features/pos/PosApp';
import { demoApiKey } from '../shared/demo';
import { t, type MessageKey } from '../shared/i18n';
import {
  ReachabilityContext,
  useReachabilityPoller,
  type Reachability,
} from '../shared/reachability';
import { SessionContext } from '../shared/session';
import { loadRaw, removeKey, saveString, STORAGE_KEYS } from '../shared/storage';
import type { Tone } from '../shared/tone';
import { Badge } from '../shared/ui';
import { ErrorBoundary } from './ErrorBoundary';

const APP_LABEL: Record<AppId, MessageKey> = {
  pos: 'app.pos',
  kitchen: 'app.kitchen',
  backoffice: 'app.backoffice',
  devtools: 'app.devtools',
};

const REACH: Record<Reachability, { tone: Tone; label: MessageKey }> = {
  up: { tone: 'ok', label: 'reach.up' },
  down: { tone: 'bad', label: 'reach.down' },
  unreachable: { tone: 'bad', label: 'reach.unreachable' },
  unknown: { tone: 'muted', label: 'reach.unknown' },
};

function loadSession(): DeviceSession | null {
  const stored = decodeSession(loadRaw(STORAGE_KEYS.session));
  const demoKey = demoApiKey();
  const s = demoKey && stored?.apiKey !== demoKey ? null : stored;
  // Set synchronously so that the first requests of child components are authenticated.
  setApiKey(s?.apiKey ?? null);
  setStaffToken(s?.staff?.token ?? null);
  return s;
}

export function App() {
  return (
    <ErrorBoundary>
      <Root />
    </ErrorBoundary>
  );
}

function Root() {
  const demoKey = demoApiKey();
  const [session, setSession] = useState<DeviceSession | null>(loadSession);
  const [signedOutReason, setSignedOutReason] = useState<string | null>(null);
  /** Staff sign-in screen requested ("Switch user") or forced (session ended). */
  const [staffScreen, setStaffScreen] = useState(false);
  const [staffReason, setStaffReason] = useState<string | null>(null);
  const sessionRef = useRef(session);
  sessionRef.current = session;

  const store = useCallback((s: DeviceSession) => {
    saveString(STORAGE_KEYS.session, encodeSession(s));
    setSession(s);
  }, []);

  const signIn = useCallback(
    (s: DeviceSession) => {
      setApiKey(s.apiKey);
      setStaffToken(s.staff?.token ?? null);
      setSignedOutReason(null);
      store(s);
    },
    [store],
  );

  const signOut = useCallback((reason: string | null = null) => {
    setApiKey(null);
    setStaffToken(null);
    removeKey(STORAGE_KEYS.session);
    setSignedOutReason(reason);
    setStaffScreen(false);
    setSession(null);
  }, []);

  const signInStaff = useCallback(
    (staff: StaffSession) => {
      const current = sessionRef.current;
      if (!current) return;
      setStaffToken(staff.token);
      setStaffReason(null);
      setStaffScreen(false);
      store({ ...current, staff });
    },
    [store],
  );

  /** Ends the person's session; the device stays paired and shows the staff sign-in screen. */
  const signOutStaff = useCallback(
    (reason: string | null, tellServer: boolean) => {
      const current = sessionRef.current;
      if (!current) return;
      if (tellServer && current.staff) void staffSignOut();
      setStaffToken(null);
      store({ apiKey: current.apiKey, me: current.me, verifiedAt: current.verifiedAt });
      setStaffReason(reason);
      setStaffScreen(true);
    },
    [store],
  );

  // A 401 anywhere means the key is unknown or was revoked: back to sign-in.
  useEffect(
    () =>
      onUnauthorized(() => {
        if (sessionRef.current) signOut(t('signin.expired'));
      }),
    [signOut],
  );

  // The person's session ended (expired, signed out elsewhere, PIN reset): back to the PIN pad.
  useEffect(
    () =>
      onStaffUnauthorized(() => {
        if (sessionRef.current) signOutStaff(t('staff.expired'), false);
      }),
    [signOutStaff],
  );

  // Re-validate the stored identity in the background (role may have changed). Offline-first:
  // when the node is unreachable the stored identity keeps working.
  const apiKey = session?.apiKey ?? null;
  useEffect(() => {
    if (!apiKey) return;
    let cancelled = false;
    void getMe().then((r) => {
      if (cancelled || !r.ok || !isMe(r.data)) return;
      const staff = sessionRef.current?.staff;
      const next: DeviceSession = {
        apiKey,
        me: r.data,
        verifiedAt: Date.now(),
        ...(staff ? { staff } : {}),
      };
      store(next);
    });
    return () => {
      cancelled = true;
    };
  }, [apiKey, store]);

  // Demo mode: connect by itself and open on the scenario tests.
  const signInDemo = useCallback(
    (s: DeviceSession) => {
      signIn(s);
      if (!globalThis.location?.hash) go({ app: 'devtools' });
    },
    [signIn],
  );

  if (!session) {
    return demoKey ? (
      <DemoConnect apiKey={demoKey} onSignedIn={signInDemo} />
    ) : (
      <SignIn onSignedIn={signIn} reason={signedOutReason} />
    );
  }
  const unpair = demoKey ? null : () => signOut(null);
  const staffRequired = needsStaffSignIn(session, Date.now());
  if (staffRequired || staffScreen) {
    return (
      <StaffSignIn
        me={session.me}
        reason={staffReason}
        onSignedIn={signInStaff}
        onCancel={staffRequired ? null : () => setStaffScreen(false)}
        onUnpair={unpair ?? (() => setStaffScreen(false))}
      />
    );
  }
  return (
    <Shell session={session} onSignOut={unpair} onSwitchUser={() => signOutStaff(null, true)} />
  );
}

function useHash(): string {
  const [hash, setHash] = useState(() => globalThis.location?.hash ?? '');
  useEffect(() => {
    const onHash = () => setHash(globalThis.location.hash);
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);
  return hash;
}

function Shell({
  session,
  onSignOut,
  onSwitchUser,
}: {
  session: DeviceSession;
  /** null in demo mode: there is nothing to sign out of. */
  onSignOut: (() => void) | null;
  /** Ends the person's session and shows the staff sign-in screen. */
  onSwitchUser: () => void;
}) {
  const { me } = session;
  const staff = session.staff?.staff ?? null;
  const role = effectiveRole(session);
  const reach = useReachabilityPoller();
  const hash = useHash();
  const route = resolveRoute(hash, role);
  const apps = appsForRole(role);
  const mainRef = useRef<HTMLElement>(null);
  const sessionValue = useMemo(
    () => ({ me, staff, role, signOut: onSignOut ?? (() => {}) }),
    [me, staff, role, onSignOut],
  );

  // Keep the URL in sync with the route actually shown (e.g. a POS device opening #/backoffice).
  const canonical = route ? formatRoute(route) : null;
  useEffect(() => {
    if (canonical && globalThis.location.hash !== canonical) {
      globalThis.history?.replaceState(null, '', canonical);
    }
  }, [canonical]);

  // Focus management: move focus to the main region when the screen changes.
  const screenKey = route ? `${route.app}/${route.app === 'backoffice' ? route.section : ''}` : '';
  const firstRender = useRef(true);
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    mainRef.current?.focus();
  }, [screenKey]);

  const r = REACH[reach.state];

  return (
    <SessionContext.Provider value={sessionValue}>
      <ReachabilityContext.Provider value={reach}>
        <a className="skip-link" href="#main">
          {t('app.skip')}
        </a>
        <div className={`app app-${route?.app ?? 'none'}`}>
          <header className="app-header no-print">
            <div className="brand">
              <strong>{t('app.brand')}</strong>
              <span className="muted small">
                {me.name} · <code>{me.terminalId}</code> · {me.branch.code}
              </span>
            </div>
            {apps.length > 1 ? (
              <nav aria-label={t('app.nav')} className="tabs">
                {apps.map((a) => (
                  <a
                    key={a}
                    href={`#/${a}`}
                    className={`tab ${route?.app === a ? 'is-active' : ''}`}
                    aria-current={route?.app === a ? 'page' : undefined}
                    onClick={(e) => {
                      e.preventDefault();
                      go(a === 'backoffice' ? { app: a, section: 'orders', id: null } : { app: a });
                    }}
                  >
                    {t(APP_LABEL[a])}
                  </a>
                ))}
              </nav>
            ) : (
              <div className="tabs" />
            )}
            <div className="header-status">
              {staff ? (
                <span className="staff-chip" data-testid="signed-in-staff">
                  <span className="staff-avatar staff-avatar-sm" aria-hidden="true">
                    {initials(staff.displayName)}
                  </span>
                  <span>{staff.displayName}</span>
                </span>
              ) : null}
              <button type="button" onClick={onSwitchUser}>
                {staff ? t('staff.switchUser') : t('staff.signIn')}
              </button>
              <Badge tone="info">{t(`role.${role}`)}</Badge>
              <span aria-live="polite" title={t('reach.title')}>
                <Badge tone={r.tone}>{t(r.label)}</Badge>
              </span>
              {onSignOut ? (
                <button type="button" onClick={onSignOut}>
                  {t('app.signOut')}
                </button>
              ) : null}
            </div>
          </header>
          {reach.state === 'unreachable' || reach.state === 'down' ? (
            <div className="banner no-print" role="alert">
              {reach.state === 'down' ? t('app.nodeDown') : t('app.nodeUnreachable')}
            </div>
          ) : null}
          <main id="main" ref={mainRef} tabIndex={-1} className="app-main">
            {route ? <Screen route={route} /> : <NoUi />}
          </main>
        </div>
      </ReachabilityContext.Provider>
    </SessionContext.Provider>
  );
}

function Screen({ route }: { route: Route }) {
  switch (route.app) {
    case 'pos':
      return <PosApp />;
    case 'kitchen':
      return <KitchenDisplay />;
    case 'backoffice':
      return <BackOffice section={route.section} id={route.id} />;
    case 'devtools':
      return <DevTools />;
  }
}

function NoUi() {
  return (
    <div className="panel panel-body no-ui" role="alert">
      <h1>{t('app.noUiTitle')}</h1>
      <p>{t('app.noUiBody')}</p>
    </div>
  );
}
