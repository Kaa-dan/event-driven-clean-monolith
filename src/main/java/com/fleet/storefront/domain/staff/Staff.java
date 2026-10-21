package com.fleet.storefront.domain.staff;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A person who signs in on store devices with a username and a numeric PIN. Repeated wrong PINs lock
 * the account for a while ({@link #MAX_FAILED_ATTEMPTS}, {@link #LOCKOUT}); a successful sign-in resets
 * the count.
 */
public record Staff(StaffId id, String username, String displayName, StaffRole role, boolean active,
                    int failedAttempts, Instant lockedUntil, Instant createdAt) {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT = Duration.ofMinutes(5);
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{1,31}");
    private static final Pattern PIN = Pattern.compile("\\d{4,8}");

    public Staff {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdAt, "createdAt");
        username = requireUsername(username);
        displayName = requireDisplayName(displayName);
        if (failedAttempts < 0) {
            throw new IllegalArgumentException("failedAttempts must not be negative");
        }
    }

    public static String requireUsername(String username) {
        String u = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(u).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 2..32 characters: lowercase letters, digits, '.', '_' or '-'");
        }
        return u;
    }

    public static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 100) {
            throw new IllegalArgumentException("Display name must be 1..100 characters");
        }
        return displayName.trim();
    }

    /** PINs are 4..8 digits. */
    public static String requirePin(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw new IllegalArgumentException("PIN must be 4..8 digits");
        }
        return pin;
    }

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** State after a wrong PIN: one more failure, and locked once the limit is reached. */
    public Staff afterFailedPin(Instant now) {
        int failures = failedAttempts + 1;
        return failures >= MAX_FAILED_ATTEMPTS
                ? new Staff(id, username, displayName, role, active, 0, now.plus(LOCKOUT), createdAt)
                : new Staff(id, username, displayName, role, active, failures, lockedUntil, createdAt);
    }

    public Staff afterSuccessfulPin() {
        return new Staff(id, username, displayName, role, active, 0, null, createdAt);
    }

    public Staff edited(String newDisplayName, StaffRole newRole, boolean nowActive) {
        return new Staff(id, username, newDisplayName, newRole, nowActive, failedAttempts, lockedUntil, createdAt);
    }
}
