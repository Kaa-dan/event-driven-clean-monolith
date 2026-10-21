package com.fleet.storefront.application.port.in;

import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.staff.StaffId;
import com.fleet.storefront.domain.staff.StaffRole;

import java.util.List;
import java.util.Objects;

/** Staff accounts for managers: create, edit, deactivate, reset PIN. */
public interface ManageStaffUseCase {

    List<Staff> staff();

    Staff create(CreateStaffCommand command);

    Staff update(UpdateStaffCommand command);

    /** Sets a new PIN, clears any lockout and signs the person out everywhere. */
    Staff resetPin(StaffId id, String pin);

    /** Creates the first manager on a fresh node unless any active manager exists. */
    boolean bootstrapManager(String username, String pin);

    record CreateStaffCommand(String username, String displayName, StaffRole role, String pin) {
        public CreateStaffCommand {
            username = Staff.requireUsername(username);
            displayName = Staff.requireDisplayName(displayName);
            Objects.requireNonNull(role, "role");
            pin = Staff.requirePin(pin);
        }
    }

    record UpdateStaffCommand(StaffId id, String displayName, StaffRole role, boolean active) {
        public UpdateStaffCommand {
            Objects.requireNonNull(id, "id");
            displayName = Staff.requireDisplayName(displayName);
            Objects.requireNonNull(role, "role");
        }
    }
}
