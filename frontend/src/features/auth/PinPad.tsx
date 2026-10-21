import { useEffect, useRef } from 'react';
import { t } from '../../shared/i18n';

export const PIN_MIN = 4;
export const PIN_MAX = 8;

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9'] as const;

/**
 * Numeric PIN entry for touch screens and keyboards. The digits are never rendered, only dots;
 * Enter (or the ✓ key) submits once at least PIN_MIN digits are in.
 */
export function PinPad({
  value,
  onChange,
  onSubmit,
  disabled = false,
  label,
}: {
  value: string;
  onChange: (pin: string) => void;
  onSubmit: () => void;
  disabled?: boolean;
  label: string;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const canSubmit = !disabled && value.length >= PIN_MIN;

  // Take focus on mount and again after each attempt (the keys are disabled while it runs, which
  // would otherwise leave keyboard users typing into nothing).
  useEffect(() => {
    if (!disabled) rootRef.current?.focus();
  }, [disabled]);

  const press = (d: string) => {
    if (disabled || value.length >= PIN_MAX) return;
    onChange(value + d);
  };
  const back = () => !disabled && onChange(value.slice(0, -1));

  return (
    <div
      ref={rootRef}
      className="pinpad"
      role="group"
      aria-label={label}
      tabIndex={-1}
      onKeyDown={(e) => {
        if (/^\d$/.test(e.key)) {
          e.preventDefault();
          press(e.key);
        } else if (e.key === 'Backspace') {
          e.preventDefault();
          back();
        } else if (e.key === 'Enter' && canSubmit) {
          e.preventDefault();
          onSubmit();
        }
      }}
    >
      <div
        className="pin-dots"
        aria-live="polite"
        aria-label={t('staff.pinEntered', { n: value.length })}
      >
        {Array.from({ length: Math.max(PIN_MIN, value.length) }, (_, i) => (
          <span key={i} className={`pin-dot ${i < value.length ? 'is-filled' : ''}`} />
        ))}
      </div>
      <div className="pin-keys">
        {KEYS.map((d) => (
          <button
            key={d}
            type="button"
            className="pin-key"
            onClick={() => press(d)}
            disabled={disabled}
          >
            {d}
          </button>
        ))}
        <button
          type="button"
          className="pin-key"
          onClick={back}
          disabled={disabled || value === ''}
          aria-label={t('staff.pinBack')}
        >
          ⌫
        </button>
        <button type="button" className="pin-key" onClick={() => press('0')} disabled={disabled}>
          0
        </button>
        <button
          type="button"
          className="pin-key pin-ok"
          onClick={onSubmit}
          disabled={!canSubmit}
          aria-label={t('staff.pinSubmit')}
        >
          ✓
        </button>
      </div>
    </div>
  );
}
