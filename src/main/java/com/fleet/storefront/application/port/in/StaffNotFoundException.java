package com.fleet.storefront.application.port.in;

import com.fleet.storefront.domain.shared.DomainException;
import com.fleet.storefront.domain.staff.StaffId;

public final class StaffNotFoundException extends DomainException {

    public StaffNotFoundException(StaffId id) {
        super("STAFF_NOT_FOUND", "No staff member " + id);
    }
}
