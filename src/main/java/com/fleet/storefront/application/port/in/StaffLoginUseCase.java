package com.fleet.storefront.application.port.in;

import com.fleet.storefront.domain.order.TerminalId;
import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.staff.StaffSession;
import com.fleet.storefront.domain.terminal.Role;

import java.util.List;
import java.util.Objects;

/** Staff sign-in on an already paired device: username + PIN → a session token for that device. */
public interface StaffLoginUseCase {

    /** Active staff who may sign in on a device of this role (for the sign-in screen). */
    List<Staff> roster(Role deviceRole);

    SignedIn signIn(SignInCommand command);

    /** Ends the session behind {@code token} (idempotent; unknown tokens are ignored). */
    void signOut(String token);

    record SignInCommand(TerminalId device, Role deviceRole, String username, String pin) {
        public SignInCommand {
            Objects.requireNonNull(device, "device");
            Objects.requireNonNull(deviceRole, "deviceRole");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(pin, "pin");
        }
    }

    /** @param token the plain session token, returned exactly once */
    record SignedIn(Staff staff, StaffSession session, String token) {
    }
}
