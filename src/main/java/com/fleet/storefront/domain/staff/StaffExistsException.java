package com.fleet.storefront.domain.staff;

import com.fleet.storefront.domain.shared.DomainException;

public final class StaffExistsException extends DomainException {

    public StaffExistsException(String username) {
        super("STAFF_EXISTS", "Username already taken: " + username);
    }
}
