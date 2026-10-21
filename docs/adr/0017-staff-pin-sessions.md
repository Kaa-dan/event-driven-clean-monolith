# 0017. Sign people in with a username and PIN on paired devices

- Status: Accepted
- Date: 2026-10-22

## Context
Device API keys (ADR 0010) identify *machines*. A restaurant also needs to know *who* took an order or a
payment, and to stop a cashier on the back-office PC from issuing refunds. Staff change devices many
times a shift, have no email and no WAN identity provider, and must sign in in seconds on a touch
screen, also while HQ is offline.

## Decision
- A `staff` account has a username, display name, role `CASHIER | WAITER | KITCHEN | MANAGER` and a 4–8
  digit PIN stored as BCrypt (port `PinHasher`; PINs have low entropy, unlike API keys). Five wrong PINs
  lock the account for 5 minutes; the failure count is committed even though the request fails.
- Signing in (`POST /staff-sessions`, on a paired device) returns an `ss_` + 256-bit token, valid 12 h;
  only its SHA-256 is stored. Requests send it as `X-Staff-Session` next to the device key. The session
  is **bound to the device** that opened it.
- Effective permission = device role ∩ staff role (`StaffRole.effectiveRole`), applied by
  `StaffSessionFilter` as the request's authority. A person never gets more than the device allows, nor
  more than their job; HQ/MONITOR devices take no sign-ins.
- `storefront.security.require-staff-session` (default `false`, `true` in the `prod` profile): when on,
  POS/KITCHEN/MANAGER devices need a person for every API call except `GET /me`, `GET /staff/roster`
  and `POST /staff-sessions`. Machine clients are unaffected.
- Orders, payments and refunds record `staff_id` through `StaffAttribution` in the same transaction —
  outside the fiscal content and its hash chain (who pressed the button is audit, not receipt data).
- Deactivating, changing the role or resetting the PIN ends the person's open sessions. The last active
  manager cannot be deactivated or demoted (`LAST_STAFF_MANAGER`). A fresh node can create its first
  manager from `STOREFRONT_BOOTSTRAP_MANAGER_USERNAME` / `_PIN`.

## Consequences
Sign-in works fully offline (one indexed lookup + BCrypt). Every audited document names a person once
staff sign-in is required. Guards: `StaffTest`, `StaffServiceTest`, `PlaceOrderServiceTest`
(attribution, not on replay), `StaffAuthIT`, `StaffSessionRequiredIT`.

## Alternatives considered
- **Passwords**: slow to type on a till; PIN + lockout + device binding is the industry norm for POS.
- **Staff identity only, no device keys**: loses per-device revocation and the device role ceiling.
- **Staff id in the fiscal document**: would change the canonical form and the hash chain of every
  document for a non-fiscal fact.
