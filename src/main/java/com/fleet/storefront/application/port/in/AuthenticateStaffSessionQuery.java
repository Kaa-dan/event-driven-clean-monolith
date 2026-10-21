package com.fleet.storefront.application.port.in;

import com.fleet.storefront.domain.order.TerminalId;
import com.fleet.storefront.domain.staff.Staff;

import java.util.Optional;

/** Resolves a staff session token to the active person, if the session is open and belongs to the device. */
public interface AuthenticateStaffSessionQuery {

    Optional<Staff> authenticate(String token, TerminalId device);
}
