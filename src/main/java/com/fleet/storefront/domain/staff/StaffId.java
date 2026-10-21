package com.fleet.storefront.domain.staff;

import java.util.Objects;
import java.util.UUID;

/** Identity of a staff member (UUIDv7). */
public record StaffId(UUID value) {

    public StaffId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
