package com.fleet.storefront.adapter.in.web;

import com.fleet.storefront.adapter.in.web.security.TerminalPrincipal;
import com.fleet.storefront.application.port.in.ManageStaffUseCase;
import com.fleet.storefront.application.port.in.ManageStaffUseCase.CreateStaffCommand;
import com.fleet.storefront.application.port.in.ManageStaffUseCase.UpdateStaffCommand;
import com.fleet.storefront.application.port.in.StaffLoginUseCase;
import com.fleet.storefront.application.port.in.StaffLoginUseCase.SignInCommand;
import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.staff.StaffId;
import com.fleet.storefront.domain.staff.StaffRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Staff sign-in on a paired device, and staff accounts for managers (ADR 0017). */
@RestController
@RequestMapping("/api/v1")
class StaffController {

    private final StaffLoginUseCase login;
    private final ManageStaffUseCase manage;

    StaffController(StaffLoginUseCase login, ManageStaffUseCase manage) {
        this.login = login;
        this.manage = manage;
    }

    /** Who may sign in on the calling device (names and roles only, for the sign-in screen). */
    @GetMapping("/staff/roster")
    List<RosterEntry> roster(@AuthenticationPrincipal TerminalPrincipal device) {
        return login.roster(device.deviceRole()).stream()
                .map(s -> new RosterEntry(s.id().value(), s.username(), s.displayName(), s.role().name()))
                .toList();
    }

    @PostMapping("/staff-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    SessionResponse signIn(@AuthenticationPrincipal TerminalPrincipal device, @Valid @RequestBody SignInRequest request) {
        StaffLoginUseCase.SignedIn signedIn = login.signIn(
                new SignInCommand(device.terminalId(), device.deviceRole(), request.username(), request.pin()));
        Staff s = signedIn.staff();
        return new SessionResponse(signedIn.token(), signedIn.session().expiresAt(),
                new RosterEntry(s.id().value(), s.username(), s.displayName(), s.role().name()),
                s.role().effectiveRole(device.deviceRole()).name());
    }

    @DeleteMapping("/staff-sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void signOut(@RequestHeader(name = "X-Staff-Session", required = false) String token) {
        login.signOut(token == null ? null : token.trim());
    }

    @GetMapping("/staff")
    List<StaffResponse> staff() {
        return manage.staff().stream().map(StaffResponse::from).toList();
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    StaffResponse create(@Valid @RequestBody CreateRequest request) {
        CreateStaffCommand command = Requests.parse(() ->
                new CreateStaffCommand(request.username(), request.displayName(), request.role(), request.pin()));
        return StaffResponse.from(manage.create(command));
    }

    @PatchMapping("/staff/{id}")
    StaffResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        UpdateStaffCommand command = Requests.parse(() ->
                new UpdateStaffCommand(new StaffId(id), request.displayName(), request.role(), request.active()));
        return StaffResponse.from(manage.update(command));
    }

    @PostMapping("/staff/{id}/pin")
    StaffResponse resetPin(@PathVariable UUID id, @Valid @RequestBody PinRequest request) {
        String pin = Requests.parse(() -> Staff.requirePin(request.pin()));
        return StaffResponse.from(manage.resetPin(new StaffId(id), pin));
    }

    record RosterEntry(UUID id, String username, String displayName, String role) {
    }

    record SessionResponse(String token, Instant expiresAt, RosterEntry staff, String effectiveRole) {
    }

    record StaffResponse(UUID id, String username, String displayName, String role, boolean active,
                         boolean locked, Instant createdAt) {
        static StaffResponse from(Staff s) {
            return new StaffResponse(s.id().value(), s.username(), s.displayName(), s.role().name(), s.active(),
                    s.lockedAt(Instant.now()), s.createdAt());
        }
    }

    record SignInRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 16) String pin) {
    }

    record CreateRequest(@NotBlank @Size(max = 32) String username, @NotBlank @Size(max = 100) String displayName,
                         @NotNull StaffRole role, @NotBlank @Size(max = 16) String pin) {
    }

    record UpdateRequest(@NotBlank @Size(max = 100) String displayName, @NotNull StaffRole role,
                         @NotNull Boolean active) {
    }

    record PinRequest(@NotBlank @Size(max = 16) String pin) {
    }
}
