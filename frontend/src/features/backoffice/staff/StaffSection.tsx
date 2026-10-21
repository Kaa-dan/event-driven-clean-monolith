import { useCallback, useState, type FormEvent } from 'react';
import { createStaff, listStaff, resetStaffPin, updateStaff } from '../../../api/endpoints';
import { STAFF_ROLES, type Problem, type StaffMember, type StaffRole } from '../../../api/types';
import { formatDateTime } from '../../../shared/formatting';
import { t } from '../../../shared/i18n';
import { useSession } from '../../../shared/session';
import {
  Badge,
  ConfirmButton,
  Field,
  FetchError,
  Notice,
  Panel,
  ProblemView,
} from '../../../shared/ui';
import { usePolling } from '../../../shared/usePolling';

const USERNAME_RE = /^[a-z0-9][a-z0-9._-]{1,31}$/;
const PIN_RE = /^\d{4,8}$/;

/** Staff accounts: who may sign in on the store's devices, with which job (ADR 0017). */
export function StaffSection() {
  const { staff: me } = useSession();
  const staff = usePolling(
    useCallback(() => listStaff(), []),
    30_000,
  );
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<StaffRole>('CASHIER');
  const [pin, setPin] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pinFor, setPinFor] = useState<StaffMember | null>(null);

  const u = username.trim().toLowerCase();
  const usernameValid = USERNAME_RE.test(u);
  const nameValid = displayName.trim().length >= 1 && displayName.trim().length <= 100;
  const pinValid = PIN_RE.test(pin);

  const done = (message: string) => {
    setNotice(message);
    staff.refresh();
  };

  const add = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (!usernameValid || !nameValid || !pinValid) return;
    setBusy(true);
    setProblem(null);
    setNotice(null);
    const r = await createStaff({ username: u, displayName: displayName.trim(), role, pin });
    setBusy(false);
    if (!r.ok) {
      setProblem(r.problem);
      return;
    }
    setUsername('');
    setDisplayName('');
    setPin('');
    setSubmitted(false);
    done(t('staffAdmin.created', { name: r.data.displayName }));
  };

  const change = async (m: StaffMember, patch: Partial<Pick<StaffMember, 'role' | 'active'>>) => {
    setProblem(null);
    setNotice(null);
    const r = await updateStaff(m.id, {
      displayName: m.displayName,
      role: patch.role ?? m.role,
      active: patch.active ?? m.active,
    });
    if (r.ok) done(t('staffAdmin.updated', { name: m.displayName }));
    else {
      setProblem(r.problem);
      staff.refresh();
    }
  };

  return (
    <div className="stack">
      {problem ? <ProblemView problem={problem} /> : null}
      {notice ? <Notice tone="ok">{notice}</Notice> : null}

      {pinFor ? (
        <ResetPin
          member={pinFor}
          onDone={(m) => {
            setPinFor(null);
            done(t('staffAdmin.pinReset', { name: m.displayName }));
          }}
          onCancel={() => setPinFor(null)}
        />
      ) : null}

      <Panel title={t('staffAdmin.add')}>
        <form onSubmit={(e) => void add(e)} noValidate>
          <div className="form-grid">
            <Field
              label={t('staffAdmin.displayName')}
              htmlFor="staff-name"
              error={submitted && !nameValid ? t('staffAdmin.err.displayName') : null}
            >
              <input
                id="staff-name"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
              />
            </Field>
            <Field
              label={t('staffAdmin.username')}
              htmlFor="staff-username"
              hint={t('staffAdmin.usernameHint')}
              error={submitted && !usernameValid ? t('staffAdmin.err.username') : null}
            >
              <input
                id="staff-username"
                value={username}
                autoCapitalize="none"
                spellCheck={false}
                onChange={(e) => setUsername(e.target.value)}
              />
            </Field>
            <Field label={t('staffAdmin.role')} htmlFor="staff-role">
              <select
                id="staff-role"
                value={role}
                onChange={(e) => setRole(e.target.value as StaffRole)}
              >
                {STAFF_ROLES.map((r) => (
                  <option key={r} value={r}>
                    {t(`staffRole.${r}`)}
                  </option>
                ))}
              </select>
            </Field>
            <Field
              label={t('staffAdmin.pin')}
              htmlFor="staff-pin"
              hint={t('staffAdmin.pinHint')}
              error={submitted && !pinValid ? t('staffAdmin.err.pin') : null}
            >
              <input
                id="staff-pin"
                type="password"
                inputMode="numeric"
                autoComplete="new-password"
                maxLength={8}
                value={pin}
                onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
              />
            </Field>
          </div>
          <button type="submit" className="btn-primary" disabled={busy}>
            {t('staffAdmin.addButton')}
          </button>
        </form>
      </Panel>

      <Panel
        title={t('staffAdmin.list')}
        actions={
          <button type="button" onClick={staff.refresh}>
            {t('common.refresh')}
          </button>
        }
      >
        {staff.result && !staff.result.ok ? <FetchError problem={staff.result.problem} /> : null}
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">{t('staffAdmin.displayName')}</th>
                <th scope="col">{t('staffAdmin.username')}</th>
                <th scope="col">{t('staffAdmin.role')}</th>
                <th scope="col">{t('staffAdmin.state')}</th>
                <th scope="col">{t('devices.createdAt')}</th>
                <th scope="col">
                  <span className="sr-only">{t('common.actions')}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {(staff.data ?? []).map((m) => {
                const self = me?.id === m.id;
                return (
                  <tr key={m.id}>
                    <td>
                      {m.displayName}
                      {self ? <span className="muted small"> ({t('staffAdmin.you')})</span> : null}
                    </td>
                    <td className="mono">{m.username}</td>
                    <td>
                      <select
                        aria-label={`${t('staffAdmin.role')}: ${m.displayName}`}
                        value={m.role}
                        disabled={!m.active}
                        onChange={(e) => void change(m, { role: e.target.value as StaffRole })}
                      >
                        {STAFF_ROLES.map((r) => (
                          <option key={r} value={r}>
                            {t(`staffRole.${r}`)}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      {m.active ? (
                        <Badge tone="ok">{t('staffAdmin.active')}</Badge>
                      ) : (
                        <Badge tone="muted">{t('staffAdmin.inactive')}</Badge>
                      )}{' '}
                      {m.locked ? <Badge tone="warn">{t('staffAdmin.locked')}</Badge> : null}
                    </td>
                    <td>{formatDateTime(m.createdAt)}</td>
                    <td>
                      <div className="btn-row">
                        <button type="button" onClick={() => setPinFor(m)}>
                          {t('staffAdmin.resetPin')}
                        </button>
                        {m.active ? (
                          self ? null : (
                            <ConfirmButton
                              label={t('staffAdmin.deactivate')}
                              confirmLabel={t('staffAdmin.deactivateConfirm')}
                              question={t('staffAdmin.deactivateQuestion', { name: m.displayName })}
                              onConfirm={() => void change(m, { active: false })}
                              danger
                            />
                          )
                        ) : (
                          <button type="button" onClick={() => void change(m, { active: true })}>
                            {t('staffAdmin.reactivate')}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Panel>
    </div>
  );
}

function ResetPin({
  member,
  onDone,
  onCancel,
}: {
  member: StaffMember;
  onDone: (m: StaffMember) => void;
  onCancel: () => void;
}) {
  const [pin, setPin] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem | null>(null);
  const valid = PIN_RE.test(pin);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (!valid) return;
    setBusy(true);
    const r = await resetStaffPin(member.id, pin);
    setBusy(false);
    if (r.ok) onDone(r.data);
    else setProblem(r.problem);
  };

  return (
    <Panel title={t('staffAdmin.newPin', { name: member.displayName })}>
      <form onSubmit={(e) => void submit(e)} noValidate className="inline">
        <Field
          label={t('staffAdmin.pin')}
          htmlFor="reset-pin"
          hint={t('staffAdmin.pinHint')}
          error={submitted && !valid ? t('staffAdmin.err.pin') : null}
        >
          <input
            id="reset-pin"
            type="password"
            inputMode="numeric"
            autoComplete="new-password"
            maxLength={8}
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
          />
        </Field>
        <div className="btn-row">
          <button type="submit" className="btn-primary" disabled={busy}>
            {t('staffAdmin.resetPin')}
          </button>
          <button type="button" onClick={onCancel}>
            {t('common.cancel')}
          </button>
        </div>
      </form>
      {problem ? <ProblemView problem={problem} /> : null}
    </Panel>
  );
}
