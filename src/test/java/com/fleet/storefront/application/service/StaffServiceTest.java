package com.fleet.storefront.application.service;

import com.fleet.storefront.application.port.in.ManageStaffUseCase.CreateStaffCommand;
import com.fleet.storefront.application.port.in.ManageStaffUseCase.UpdateStaffCommand;
import com.fleet.storefront.application.port.in.StaffLoginUseCase.SignInCommand;
import com.fleet.storefront.application.port.in.StaffLoginUseCase.SignedIn;
import com.fleet.storefront.application.port.out.PinHasher;
import com.fleet.storefront.application.port.out.StaffDirectory;
import com.fleet.storefront.domain.order.TerminalId;
import com.fleet.storefront.domain.staff.InvalidStaffCredentialsException;
import com.fleet.storefront.domain.staff.LastStaffManagerException;
import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.staff.StaffExistsException;
import com.fleet.storefront.domain.staff.StaffId;
import com.fleet.storefront.domain.staff.StaffLockedException;
import com.fleet.storefront.domain.staff.StaffNotAllowedOnDeviceException;
import com.fleet.storefront.domain.staff.StaffRole;
import com.fleet.storefront.domain.staff.StaffSession;
import com.fleet.storefront.domain.terminal.Role;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffServiceTest {

    private static final TerminalId POS = new TerminalId("pos-1");
    private static final TerminalId OTHER_POS = new TerminalId("pos-2");
    private static final TerminalId KDS = new TerminalId("kds-1");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-22T12:00:00Z"));
    private final FakeDirectory directory = new FakeDirectory();
    private final StaffService service = new StaffService(directory, new FakePinHasher(), new InMemoryStorefront(),
            clock);

    @Test
    void signingInIssuesADeviceBoundSessionThatAuthenticatesUntilSignOut() {
        Staff ana = service.create(new CreateStaffCommand("ana", "Ana", StaffRole.CASHIER, "1234"));

        SignedIn signedIn = service.signIn(new SignInCommand(POS, Role.POS, "ANA ", "1234"));

        assertThat(directory.sessions).doesNotContainKey(signedIn.token());
        assertThat(service.authenticate(signedIn.token(), POS)).map(Staff::id).contains(ana.id());
        assertThat(service.authenticate(signedIn.token(), OTHER_POS)).isEmpty();
        assertThat(service.authenticate("ss_wrong", POS)).isEmpty();
        assertThat(service.authenticate(null, POS)).isEmpty();

        service.signOut(signedIn.token());
        assertThat(service.authenticate(signedIn.token(), POS)).isEmpty();
    }

    @Test
    void sessionsExpire() {
        service.create(new CreateStaffCommand("ana", "Ana", StaffRole.CASHIER, "1234"));
        SignedIn signedIn = service.signIn(new SignInCommand(POS, Role.POS, "ana", "1234"));

        clock.advance(StaffSession.LIFETIME);

        assertThat(service.authenticate(signedIn.token(), POS)).isEmpty();
    }

    @Test
    void wrongPinsAndUnknownUsersLookTheSame_andTheFifthWrongPinLocksTheAccount() {
        service.create(new CreateStaffCommand("ana", "Ana", StaffRole.CASHIER, "1234"));

        assertThatThrownBy(() -> service.signIn(new SignInCommand(POS, Role.POS, "nobody", "1234")))
                .isInstanceOf(InvalidStaffCredentialsException.class);
        for (int i = 1; i < Staff.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.signIn(new SignInCommand(POS, Role.POS, "ana", "0000")))
                    .isInstanceOf(InvalidStaffCredentialsException.class);
        }
        assertThatThrownBy(() -> service.signIn(new SignInCommand(POS, Role.POS, "ana", "0000")))
                .isInstanceOf(StaffLockedException.class);
        // Locked: even the right PIN is refused until the lockout ends.
        assertThatThrownBy(() -> service.signIn(new SignInCommand(POS, Role.POS, "ana", "1234")))
                .isInstanceOf(StaffLockedException.class);

        clock.advance(Staff.LOCKOUT);
        assertThat(service.signIn(new SignInCommand(POS, Role.POS, "ana", "1234")).staff().failedAttempts()).isZero();
    }

    @Test
    void aPersonCanOnlySignInOnDevicesTheirJobAllows() {
        service.create(new CreateStaffCommand("ana", "Ana", StaffRole.CASHIER, "1234"));
        service.create(new CreateStaffCommand("chef", "Chef", StaffRole.KITCHEN, "3333"));

        assertThatThrownBy(() -> service.signIn(new SignInCommand(KDS, Role.KITCHEN, "ana", "1234")))
                .isInstanceOf(StaffNotAllowedOnDeviceException.class);
        assertThat(service.roster(Role.KITCHEN)).extracting(Staff::username).containsExactly("chef");
        assertThat(service.roster(Role.POS)).extracting(Staff::username).containsExactly("ana");
        assertThat(service.roster(Role.HQ)).isEmpty();
    }

    @Test
    void deactivatingOrChangingTheRoleEndsOpenSessions_andInactiveStaffCannotSignIn() {
        Staff ana = service.create(new CreateStaffCommand("ana", "Ana", StaffRole.CASHIER, "1234"));
        SignedIn signedIn = service.signIn(new SignInCommand(POS, Role.POS, "ana", "1234"));

        service.update(new UpdateStaffCommand(ana.id(), "Ana B.", StaffRole.CASHIER, false));

        assertThat(service.authenticate(signedIn.token(), POS)).isEmpty();
        assertThatThrownBy(() -> service.signIn(new SignInCommand(POS, Role.POS, "ana", "1234")))
                .isInstanceOf(InvalidStaffCredentialsException.class);
    }

    @Test
    void resettingThePinUnlocksAndSignsOutEverywhere() {
        Staff ana = service.create(new CreateStaffCommand("ana", "Ana", StaffRole.CASHIER, "1234"));
        SignedIn signedIn = service.signIn(new SignInCommand(POS, Role.POS, "ana", "1234"));
        for (int i = 0; i < Staff.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.signIn(new SignInCommand(OTHER_POS, Role.POS, "ana", "0000")));
        }

        service.resetPin(ana.id(), "5678");

        assertThat(service.authenticate(signedIn.token(), POS)).isEmpty();
        assertThat(service.signIn(new SignInCommand(POS, Role.POS, "ana", "5678")).staff().username()).isEqualTo("ana");
        assertThatThrownBy(() -> service.signIn(new SignInCommand(POS, Role.POS, "ana", "1234")))
                .isInstanceOf(InvalidStaffCredentialsException.class);
    }

    @Test
    void usernamesAreUnique_andTheLastActiveManagerCannotBeDeactivatedOrDemoted() {
        Staff boss = service.create(new CreateStaffCommand("boss", "Boss", StaffRole.MANAGER, "1234"));
        assertThatThrownBy(() -> service.create(new CreateStaffCommand("BOSS", "Again", StaffRole.WAITER, "1111")))
                .isInstanceOf(StaffExistsException.class);

        assertThatThrownBy(() -> service.update(new UpdateStaffCommand(boss.id(), "Boss", StaffRole.MANAGER, false)))
                .isInstanceOf(LastStaffManagerException.class);
        assertThatThrownBy(() -> service.update(new UpdateStaffCommand(boss.id(), "Boss", StaffRole.WAITER, true)))
                .isInstanceOf(LastStaffManagerException.class);

        service.create(new CreateStaffCommand("deputy", "Deputy", StaffRole.MANAGER, "2222"));
        assertThat(service.update(new UpdateStaffCommand(boss.id(), "Boss", StaffRole.WAITER, true)).role())
                .isEqualTo(StaffRole.WAITER);
    }

    @Test
    void theBootstrapManagerIsCreatedOnlyOnAFreshNode() {
        assertThat(service.bootstrapManager("", "")).isFalse();
        assertThat(service.bootstrapManager("owner", "9876")).isTrue();
        assertThat(service.bootstrapManager("owner2", "9876")).isFalse();
        assertThat(service.signIn(new SignInCommand(POS, Role.MANAGER, "owner", "9876")).staff().role())
                .isEqualTo(StaffRole.MANAGER);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Reversible stand-in for BCrypt. */
    private static final class FakePinHasher implements PinHasher {
        @Override
        public String hash(String pin) {
            return "h:" + pin;
        }

        @Override
        public boolean matches(String pin, String hash) {
            return hash != null && hash.equals("h:" + pin);
        }
    }

    private static final class FakeDirectory implements StaffDirectory {
        final Map<StaffId, Staff> staff = new HashMap<>();
        final Map<StaffId, String> pins = new HashMap<>();
        /** token hash → session */
        final Map<String, StaffSession> sessions = new HashMap<>();

        @Override
        public Optional<Staff> findById(StaffId id) {
            return Optional.ofNullable(staff.get(id));
        }

        @Override
        public Optional<Staff> findByUsername(String username) {
            return staff.values().stream().filter(s -> s.username().equals(username)).findFirst();
        }

        @Override
        public Optional<StaffCredentials> findCredentialsForUpdate(String username) {
            return findByUsername(username).map(s -> new StaffCredentials(s, pins.get(s.id())));
        }

        @Override
        public List<Staff> findAll() {
            List<Staff> all = new ArrayList<>(staff.values());
            all.sort(Comparator.comparing(Staff::username));
            return all;
        }

        @Override
        public void insert(Staff s, String pinHash) {
            staff.put(s.id(), s);
            pins.put(s.id(), pinHash);
        }

        @Override
        public void update(Staff s) {
            staff.put(s.id(), s);
        }

        @Override
        public void updatePin(StaffId id, String pinHash) {
            pins.put(id, pinHash);
        }

        @Override
        public long countActiveForUpdate(StaffRole role) {
            return staff.values().stream().filter(s -> s.active() && s.role() == role).count();
        }

        @Override
        public void insertSession(StaffSession session, String tokenHash) {
            sessions.put(tokenHash, session);
        }

        @Override
        public Optional<StaffSession> findSessionByTokenHash(String tokenHash) {
            return Optional.ofNullable(sessions.get(tokenHash));
        }

        @Override
        public void endSession(String tokenHash, Instant at) {
            sessions.computeIfPresent(tokenHash, (k, s) -> ended(s, at));
        }

        @Override
        public void endSessionsOf(StaffId id, Instant at) {
            sessions.replaceAll((k, s) -> s.staffId().equals(id) ? ended(s, at) : s);
        }

        private static StaffSession ended(StaffSession s, Instant at) {
            return s.endedAt() != null ? s
                    : new StaffSession(s.id(), s.staffId(), s.terminalId(), s.createdAt(), s.expiresAt(), at);
        }
    }
}
