package com.fleet.storefront.domain.staff;

import com.fleet.storefront.domain.shared.DomainException;

/** Unknown username, inactive account or wrong PIN — deliberately indistinguishable. */
public final class InvalidStaffCredentialsException extends DomainException {

    public InvalidStaffCredentialsException() {
        super("INVALID_CREDENTIALS", "Unknown username or wrong PIN");
    }
}
