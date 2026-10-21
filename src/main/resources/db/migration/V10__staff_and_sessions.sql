-- Staff accounts (ADR 0017). People sign in with username + PIN on an already paired device. Only a
-- BCrypt hash of the PIN is stored; repeated wrong PINs lock the account for a while.
CREATE TABLE staff (
    id              UUID         PRIMARY KEY,
    username        VARCHAR(32)  NOT NULL UNIQUE,
    display_name    VARCHAR(100) NOT NULL,
    role            VARCHAR(16)  NOT NULL CHECK (role IN ('CASHIER', 'WAITER', 'KITCHEN', 'MANAGER')),
    pin_hash        VARCHAR(100) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER      NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL
);

-- A person signed in on one device. Only the SHA-256 of the session token is stored.
CREATE TABLE staff_session (
    id          UUID        PRIMARY KEY,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    staff_id    UUID        NOT NULL REFERENCES staff (id),
    terminal_id VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    ended_at    TIMESTAMPTZ
);
CREATE INDEX staff_session_open_idx ON staff_session (staff_id) WHERE ended_at IS NULL;

-- Who issued each document (audit; not part of the fiscal content or its hash). NULL for documents
-- issued by a device without a signed-in person, and for everything before this migration.
ALTER TABLE customer_order ADD COLUMN staff_id UUID;
ALTER TABLE payment        ADD COLUMN staff_id UUID;
ALTER TABLE refund         ADD COLUMN staff_id UUID;
