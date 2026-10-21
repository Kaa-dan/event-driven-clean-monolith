package com.fleet.storefront.domain.staff;

import com.fleet.storefront.domain.order.TerminalId;
import com.fleet.storefront.domain.shared.Sha256;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * A person signed in on one device. The token ({@code ss_} + 256 random bits) is returned once; only its
 * SHA-256 is stored, as for device API keys. A session is bound to the device it was opened on.
 */
public record StaffSession(UUID id, StaffId staffId, TerminalId terminalId, Instant createdAt, Instant expiresAt,
                           Instant endedAt) {

    public static final Duration LIFETIME = Duration.ofHours(12);
    public static final int MAX_TOKEN_LENGTH = 200;
    private static final SecureRandom RANDOM = new SecureRandom();

    public StaffSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(staffId, "staffId");
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean validAt(Instant now) {
        return endedAt == null && now.isBefore(expiresAt);
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "ss_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        return Sha256.hex(token);
    }
}
