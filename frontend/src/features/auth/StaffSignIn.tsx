import { useCallback, useEffect, useState } from 'react';
import { getRoster, staffSignIn } from '../../api/endpoints';
import type { Me, Problem, RosterEntry } from '../../api/types';
import type { StaffSession } from '../../domain/session';
import { formatTime } from '../../shared/formatting';
import { t } from '../../shared/i18n';
import { initials } from '../../domain/staff';
import { Notice, ProblemView } from '../../shared/ui';
import { PinPad } from './PinPad';

/**
 * Who is working on this device: pick your name, enter your PIN (ADR 0017). The device stays paired;
 * only the person changes.
 */
export function StaffSignIn({
  me,
  onSignedIn,
  onCancel,
  onUnpair,
  reason,
}: {
  me: Me;
  onSignedIn: (s: StaffSession) => void;
  /** Present when signing in is optional: go back to the app without a person. */
  onCancel: (() => void) | null;
  onUnpair: () => void;
  reason: string | null;
}) {
  const [roster, setRoster] = useState<RosterEntry[] | null>(null);
  const [rosterProblem, setRosterProblem] = useState<Problem | null>(null);
  const [who, setWho] = useState<RosterEntry | null>(null);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [problem, setProblem] = useState<Problem | null>(null);

  const load = useCallback(async () => {
    setRosterProblem(null);
    const r = await getRoster();
    if (r.ok) setRoster(r.data);
    else setRosterProblem(r.problem);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const choose = (p: RosterEntry | null) => {
    setWho(p);
    setPin('');
    setError(null);
    setProblem(null);
  };

  const submit = async () => {
    if (!who || busy) return;
    setBusy(true);
    setError(null);
    setProblem(null);
    const r = await staffSignIn({ username: who.username, pin });
    setBusy(false);
    setPin('');
    if (r.ok) {
      onSignedIn({
        token: r.data.token,
        expiresAt: r.data.expiresAt,
        staff: r.data.staff,
        effectiveRole: r.data.effectiveRole,
      });
      return;
    }
    const code = r.problem.code;
    if (code === 'INVALID_CREDENTIALS') setError(t('staff.wrongPin'));
    else if (code === 'STAFF_LOCKED') {
      const until = typeof r.problem.lockedUntil === 'string' ? r.problem.lockedUntil : null;
      setError(until ? t('staff.lockedUntil', { at: formatTime(until) }) : t('staff.locked'));
    } else if (code === 'STAFF_NOT_ALLOWED_ON_DEVICE') setError(t('staff.notAllowed'));
    else setProblem(r.problem);
  };

  return (
    <main className="signin" id="main">
      <div className="signin-card staff-card panel" aria-labelledby="staff-title">
        <div className="staff-card-head">
          <div>
            <p className="eyebrow">{me.name}</p>
            <h1 id="staff-title">
              {who ? t('staff.enterPin', { name: who.displayName }) : t('staff.whoAreYou')}
            </h1>
          </div>
          <span className="muted small mono">{me.branch.code}</span>
        </div>
        {reason ? <Notice tone="warn">{reason}</Notice> : null}

        {who ? (
          <>
            <PinPad
              value={pin}
              onChange={(v) => {
                setPin(v);
                setError(null);
              }}
              onSubmit={() => void submit()}
              disabled={busy}
              label={t('staff.pinLabel', { name: who.displayName })}
            />
            {error ? (
              <p className="problem" role="alert">
                <strong>{error}</strong>
              </p>
            ) : null}
            {problem ? <ProblemView problem={problem} /> : null}
            <button type="button" className="btn-lg" onClick={() => choose(null)}>
              {t('staff.notMe')}
            </button>
          </>
        ) : (
          <>
            {rosterProblem ? (
              <>
                <ProblemView problem={rosterProblem} />
                <button type="button" className="btn-lg" onClick={() => void load()}>
                  {t('common.refresh')}
                </button>
              </>
            ) : null}
            {roster === null && !rosterProblem ? (
              <p className="muted">{t('common.loading')}</p>
            ) : null}
            {roster && roster.length === 0 ? (
              <Notice tone="warn">{t('staff.nobody')}</Notice>
            ) : null}
            {roster && roster.length > 0 ? (
              <ul className="staff-tiles" aria-label={t('staff.rosterLabel')}>
                {roster.map((p) => (
                  <li key={p.id}>
                    <button type="button" className="staff-tile" onClick={() => choose(p)}>
                      <span className="staff-avatar" aria-hidden="true">
                        {initials(p.displayName)}
                      </span>
                      <span className="staff-name">{p.displayName}</span>
                      <span className="muted small">{t(`staffRole.${p.role}`)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </>
        )}

        <div className="btn-row staff-card-foot">
          {onCancel ? (
            <button type="button" onClick={onCancel}>
              {t('staff.skip')}
            </button>
          ) : null}
          <button type="button" className="link-button" onClick={onUnpair}>
            {t('staff.unpair')}
          </button>
        </div>
      </div>
    </main>
  );
}
