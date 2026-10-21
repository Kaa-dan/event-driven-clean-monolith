package com.fleet.storefront.adapter.in.web.security;

import com.fleet.storefront.domain.order.TerminalId;
import com.fleet.storefront.domain.staff.StaffId;
import com.fleet.storefront.domain.staff.StaffRole;
import com.fleet.storefront.domain.terminal.Role;

/**
 * The authenticated device behind a request (inject with {@code @AuthenticationPrincipal}), and the person
 * signed in on it, if any.
 *
 * @param role       what the request may do: the device role, narrowed by the person's role when signed in
 * @param deviceRole the device's own role
 * @param staff      the signed-in person; {@code null} for a device-only request
 */
public record TerminalPrincipal(TerminalId terminalId, String name, Role role, Role deviceRole, Staff staff) {

    public TerminalPrincipal(TerminalId terminalId, String name, Role role) {
        this(terminalId, name, role, role, null);
    }

    public StaffId staffId() {
        return staff == null ? null : staff.id();
    }

    public record Staff(StaffId id, String username, String displayName, StaffRole role) {
    }
}
