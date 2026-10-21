package com.fleet.storefront.application.service;

import com.fleet.storefront.application.port.in.AuthenticateStaffSessionQuery;
import com.fleet.storefront.application.port.in.ManageStaffUseCase;
import com.fleet.storefront.application.port.in.StaffLoginUseCase;
import com.fleet.storefront.application.port.in.StaffNotFoundException;
import com.fleet.storefront.application.port.out.PinHasher;
import com.fleet.storefront.application.port.out.StaffDirectory;
import com.fleet.storefront.application.port.out.TransactionRunner;
import com.fleet.storefront.domain.order.TerminalId;
import com.fleet.storefront.domain.shared.UuidV7;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Staff accounts and PIN sign-in. Only PIN hashes (slow KDF) and session-token hashes are stored. A wrong
 * PIN is committed as a failure (and eventually a lockout) before the error is raised, so the count
 * survives the rejected request.
 */
public final class StaffService implements StaffLoginUseCase, AuthenticateStaffSessionQuery, ManageStaffUseCase {

    private final StaffDirectory directory;
    private final PinHasher pins;
    private final TransactionRunner transactions;
    private final Clock clock;

    public StaffService(StaffDirectory directory, PinHasher pins, TransactionRunner transactions, Clock clock) {
        this.directory = directory;
        this.pins = pins;
        this.transactions = transactions;
        this.clock = clock;
    }

    // --- sign-in ------------------------------------------------------------------------------

    @Override
    public List<Staff> roster(Role deviceRole) {
        return directory.findAll().stream()
                .filter(Staff::active)
                .filter(s -> s.role().allowedOn(deviceRole))
                .toList();
    }

    private sealed interface Attempt {
    }

    private record Success(SignedIn signedIn) implements Attempt {
    }

    private record Failure(RuntimeException error) implements Attempt {
    }

    @Override
    public SignedIn signIn(SignInCommand command) {
        String username;
        try {
            username = Staff.requireUsername(command.username());
        } catch (IllegalArgumentException e) {
            throw new InvalidStaffCredentialsException();
        }
        Attempt attempt = transactions.inTransaction(() -> attempt(command, username));
        return switch (attempt) {
            case Success s -> s.signedIn();
            case Failure f -> throw f.error();
        };
    }

    private Attempt attempt(SignInCommand command, String username) {
        Instant now = clock.instant();
        Optional<StaffDirectory.StaffCredentials> found = directory.findCredentialsForUpdate(username);
        if (found.isEmpty() || !found.get().staff().active()) {
            // Spend the same time as a real check so usernames cannot be probed by timing.
            pins.matches(command.pin(), null);
            return new Failure(new InvalidStaffCredentialsException());
        }
        Staff staff = found.get().staff();
        if (staff.lockedAt(now)) {
            return new Failure(new StaffLockedException(staff.lockedUntil()));
        }
        if (!pins.matches(command.pin(), found.get().pinHash())) {
            Staff failed = staff.afterFailedPin(now);
            directory.update(failed);
            return new Failure(failed.lockedAt(now)
                    ? new StaffLockedException(failed.lockedUntil())
                    : new InvalidStaffCredentialsException());
        }
        if (!staff.role().allowedOn(command.deviceRole())) {
            return new Failure(new StaffNotAllowedOnDeviceException(staff.role(), command.deviceRole()));
        }
        Staff signedIn = staff.afterSuccessfulPin();
        if (!signedIn.equals(staff)) {
            directory.update(signedIn);
        }
        String token = StaffSession.newToken();
        StaffSession session = new StaffSession(UuidV7.next(), staff.id(), command.device(), now,
                now.plus(StaffSession.LIFETIME), null);
        directory.insertSession(session, StaffSession.hash(token));
        return new Success(new SignedIn(signedIn, session, token));
    }

    @Override
    public void signOut(String token) {
        if (validToken(token)) {
            directory.endSession(StaffSession.hash(token), clock.instant());
        }
    }

    @Override
    public Optional<Staff> authenticate(String token, TerminalId device) {
        if (!validToken(token)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return directory.findSessionByTokenHash(StaffSession.hash(token))
                .filter(s -> s.validAt(now) && s.terminalId().equals(device))
                .flatMap(s -> directory.findById(s.staffId()))
                .filter(Staff::active);
    }

    private static boolean validToken(String token) {
        return token != null && !token.isBlank() && token.length() <= StaffSession.MAX_TOKEN_LENGTH;
    }

    // --- management ---------------------------------------------------------------------------

    @Override
    public List<Staff> staff() {
        return directory.findAll();
    }

    @Override
    public Staff create(CreateStaffCommand command) {
        String pinHash = pins.hash(command.pin());
        return transactions.inTransaction(() -> {
            if (directory.findByUsername(command.username()).isPresent()) {
                throw new StaffExistsException(command.username());
            }
            Staff staff = new Staff(new StaffId(UuidV7.next()), command.username(), command.displayName(),
                    command.role(), true, 0, null, clock.instant());
            directory.insert(staff, pinHash);
            return staff;
        });
    }

    @Override
    public Staff update(UpdateStaffCommand command) {
        return transactions.inTransaction(() -> {
            Staff current = directory.findById(command.id()).orElseThrow(() -> new StaffNotFoundException(command.id()));
            boolean losesManager = current.active() && current.role() == StaffRole.MANAGER
                    && (!command.active() || command.role() != StaffRole.MANAGER);
            if (losesManager && directory.countActiveForUpdate(StaffRole.MANAGER) <= 1) {
                throw new LastStaffManagerException();
            }
            Staff updated = current.edited(command.displayName(), command.role(), command.active());
            directory.update(updated);
            if (!updated.active() || updated.role() != current.role()) {
                directory.endSessionsOf(updated.id(), clock.instant());
            }
            return updated;
        });
    }

    @Override
    public Staff resetPin(StaffId id, String pin) {
        String pinHash = pins.hash(Staff.requirePin(pin));
        return transactions.inTransaction(() -> {
            Staff current = directory.findById(id).orElseThrow(() -> new StaffNotFoundException(id));
            Staff unlocked = current.afterSuccessfulPin();
            directory.update(unlocked);
            directory.updatePin(id, pinHash);
            directory.endSessionsOf(id, clock.instant());
            return unlocked;
        });
    }

    @Override
    public boolean bootstrapManager(String username, String pin) {
        if (username == null || username.isBlank() || pin == null || pin.isBlank()) {
            return false;
        }
        CreateStaffCommand command = new CreateStaffCommand(username, "Manager", StaffRole.MANAGER, pin);
        String pinHash = pins.hash(command.pin());
        return transactions.inTransaction(() -> {
            if (directory.countActiveForUpdate(StaffRole.MANAGER) > 0
                    || directory.findByUsername(command.username()).isPresent()) {
                return false;
            }
            directory.insert(new Staff(new StaffId(UuidV7.next()), command.username(), command.displayName(),
                    StaffRole.MANAGER, true, 0, null, clock.instant()), pinHash);
            return true;
        });
    }
}
