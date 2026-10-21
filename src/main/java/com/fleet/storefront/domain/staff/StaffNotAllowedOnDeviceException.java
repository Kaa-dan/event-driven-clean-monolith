package com.fleet.storefront.domain.staff;

import com.fleet.storefront.domain.shared.DomainException;
import com.fleet.storefront.domain.terminal.Role;

public final class StaffNotAllowedOnDeviceException extends DomainException {

    public StaffNotAllowedOnDeviceException(StaffRole staff, Role device) {
        super("STAFF_NOT_ALLOWED_ON_DEVICE", "A " + staff + " cannot sign in on a " + device + " device");
    }
}
