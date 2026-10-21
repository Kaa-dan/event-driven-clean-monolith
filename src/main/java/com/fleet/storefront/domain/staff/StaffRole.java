package com.fleet.storefront.domain.staff;

import com.fleet.storefront.domain.terminal.Role;

/**
 * What a person does in the store. What they may do on a given device is the intersection with the
 * device's {@link Role}: a person never gains more than the device allows, and a device never lets a
 * person do more than their job.
 */
public enum StaffRole {
    /** Takes counter orders and payments. */
    CASHIER,
    /** Serves tables; takes orders and payments. */
    WAITER,
    /** Works the kitchen display. */
    KITCHEN,
    /** Everything the device allows, including refunds, catalog, stock, staff and reports. */
    MANAGER;

    /** Whether this person may sign in on a device of the given role at all. */
    public boolean allowedOn(Role device) {
        return effectiveRole(device) != null;
    }

    /**
     * The role the person acts with on {@code device}, or {@code null} if they may not use it. Machine
     * devices (HQ, MONITOR) never take staff sign-ins.
     */
    public Role effectiveRole(Role device) {
        return switch (device) {
            case POS -> this == KITCHEN ? null : Role.POS;
            case KITCHEN -> this == KITCHEN || this == MANAGER ? Role.KITCHEN : null;
            case MANAGER -> switch (this) {
                case MANAGER -> Role.MANAGER;
                case CASHIER, WAITER -> Role.POS;
                case KITCHEN -> Role.KITCHEN;
            };
            case HQ, MONITOR -> null;
        };
    }
}
