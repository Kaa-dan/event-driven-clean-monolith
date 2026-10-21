package com.fleet.storefront.domain.staff;

import com.fleet.storefront.domain.shared.DomainException;

public final class LastStaffManagerException extends DomainException {

    public LastStaffManagerException() {
        super("LAST_STAFF_MANAGER", "The last active manager cannot be deactivated or demoted; add another manager first");
    }
}
