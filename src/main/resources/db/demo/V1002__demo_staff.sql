-- Demo staff (profile "demo" only). PINs, for local testing ONLY:
--   maria (MANAGER) 1234 · carlos (CASHIER) 1111 · wendy (WAITER) 2222 · kofi (KITCHEN) 3333
INSERT INTO staff (id, username, display_name, role, pin_hash, created_at) VALUES
    ('01926b3a-0000-7000-8000-0000000000b1', 'maria',  'Maria (manager)', 'MANAGER', '$2a$10$EIXkasXzSMMnLt2TLGQ0N.uw6/2HhtWH2QDdxC/mnuw/ou7Zr4N.y', now()),
    ('01926b3a-0000-7000-8000-0000000000b2', 'carlos', 'Carlos',          'CASHIER', '$2a$10$lyCI.UZFt.xzFlCUvIGTIeTH8VtH80h.3drNL4aAcWUwtWhWsVpO2', now()),
    ('01926b3a-0000-7000-8000-0000000000b3', 'wendy',  'Wendy',           'WAITER',  '$2a$10$hv5nn..1bGVB8cDFsglT4ODTBMthczBZPowi.Mng1P5yWgQxzdqIO', now()),
    ('01926b3a-0000-7000-8000-0000000000b4', 'kofi',   'Kofi',            'KITCHEN', '$2a$10$Hi4e7gx/eM9i0wrqbH2VaeMmjaOt2vLktYRo5sMMXYdB.1e/oYpfO', now());
