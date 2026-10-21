package com.fleet.storefront.domain;

import com.fleet.storefront.domain.order.TerminalId;
import com.fleet.storefront.domain.shared.Sha256;
import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.staff.StaffId;
import com.fleet.storefront.domain.staff.StaffRole;
import com.fleet.storefront.domain.staff.StaffSession;
import com.fleet.storefront.domain.terminal.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    private static Staff staff(StaffRole role) {
        return new Staff(new StaffId(UUID.randomUUID()), "Ana.B", "Ana", role, true, 0, null, NOW);
    }

    @Test
    void usernamesAreNormalisedAndValidated_pinsAreFourToEightDigits() {
        assertThat(staff(StaffRole.WAITER).username()).isEqualTo("ana.b");
        assertThatThrownBy(() -> Staff.requireUsername("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Staff.requireUsername("has space")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Staff.requirePin("1234")).isEqualTo("1234");
        assertThat(Staff.requirePin("12345678")).isEqualTo("12345678");
        assertThatThrownBy(() -> Staff.requirePin("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Staff.requirePin("12a4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Staff.requirePin(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFifthWrongPinLocksTheAccountForFiveMinutes_andASuccessResetsTheCount() {
        Staff s = staff(StaffRole.CASHIER);
        for (int i = 1; i < Staff.MAX_FAILED_ATTEMPTS; i++) {
            s = s.afterFailedPin(NOW);
            assertThat(s.failedAttempts()).isEqualTo(i);
            assertThat(s.lockedAt(NOW)).isFalse();
        }
        s = s.afterFailedPin(NOW);
        assertThat(s.lockedAt(NOW)).isTrue();
        assertThat(s.lockedAt(NOW.plus(Staff.LOCKOUT))).isFalse();
        assertThat(s.failedAttempts()).isZero();

        assertThat(staff(StaffRole.CASHIER).afterFailedPin(NOW).afterSuccessfulPin().failedAttempts()).isZero();
    }

    @Test
    void aPersonNeverGetsMoreThanTheDeviceAllowsNorMoreThanTheirJob() {
        assertThat(StaffRole.CASHIER.effectiveRole(Role.POS)).isEqualTo(Role.POS);
        assertThat(StaffRole.WAITER.effectiveRole(Role.POS)).isEqualTo(Role.POS);
        assertThat(StaffRole.MANAGER.effectiveRole(Role.POS)).isEqualTo(Role.POS);
        assertThat(StaffRole.KITCHEN.effectiveRole(Role.POS)).isNull();

        assertThat(StaffRole.KITCHEN.effectiveRole(Role.KITCHEN)).isEqualTo(Role.KITCHEN);
        assertThat(StaffRole.MANAGER.effectiveRole(Role.KITCHEN)).isEqualTo(Role.KITCHEN);
        assertThat(StaffRole.CASHIER.allowedOn(Role.KITCHEN)).isFalse();

        assertThat(StaffRole.MANAGER.effectiveRole(Role.MANAGER)).isEqualTo(Role.MANAGER);
        assertThat(StaffRole.CASHIER.effectiveRole(Role.MANAGER)).isEqualTo(Role.POS);
        assertThat(StaffRole.KITCHEN.effectiveRole(Role.MANAGER)).isEqualTo(Role.KITCHEN);

        for (StaffRole r : StaffRole.values()) {
            assertThat(r.allowedOn(Role.HQ)).isFalse();
            assertThat(r.allowedOn(Role.MONITOR)).isFalse();
        }
    }

    @Test
    void sessionTokensAreHighEntropy_storedOnlyAsHashes_andExpire() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(StaffSession.newToken());
        }
        assertThat(tokens).hasSize(1000).allMatch(t -> t.matches("ss_[A-Za-z0-9_-]{43}"));
        String token = tokens.iterator().next();
        assertThat(StaffSession.hash(token)).isEqualTo(Sha256.hex(token)).isNotEqualTo(token);

        StaffSession session = new StaffSession(UUID.randomUUID(), new StaffId(UUID.randomUUID()),
                new TerminalId("pos-1"), NOW, NOW.plus(StaffSession.LIFETIME), null);
        assertThat(session.validAt(NOW)).isTrue();
        assertThat(session.validAt(NOW.plus(StaffSession.LIFETIME))).isFalse();
        assertThat(new StaffSession(session.id(), session.staffId(), session.terminalId(), NOW, session.expiresAt(),
                NOW).validAt(NOW)).isFalse();
    }
}
