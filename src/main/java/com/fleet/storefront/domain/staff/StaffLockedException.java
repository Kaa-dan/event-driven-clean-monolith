package com.fleet.storefront.domain.staff;

import com.fleet.storefront.domain.shared.DomainException;

import java.time.Instant;
import java.util.Map;

public final class StaffLockedException extends DomainException {

    private final Instant lockedUntil;

    public StaffLockedException(Instant lockedUntil) {
        super("STAFF_LOCKED", "Too many wrong PINs; try again after " + lockedUntil);
        this.lockedUntil = lockedUntil;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("lockedUntil", lockedUntil.toString());
    }
}
