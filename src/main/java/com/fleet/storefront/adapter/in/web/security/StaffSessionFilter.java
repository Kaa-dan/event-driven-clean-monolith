package com.fleet.storefront.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.storefront.application.port.in.AuthenticateStaffSessionQuery;
import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.terminal.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code X-Staff-Session: <token>} on an authenticated device → the signed-in person (ADR 0017). The
 * session must have been opened on this very device; the request's authority becomes the device role
 * narrowed by the person's role. With {@code requireStaffSession}, UI devices (POS, KITCHEN, MANAGER)
 * must send a session on every API call except the few needed to sign in.
 */
class StaffSessionFilter extends OncePerRequestFilter {

    static final String STAFF_SESSION_HEADER = "X-Staff-Session";
    private static final Set<Role> UI_DEVICES = Set.of(Role.POS, Role.KITCHEN, Role.MANAGER);

    private final AuthenticateStaffSessionQuery sessions;
    private final ObjectMapper json;
    private final boolean requireStaffSession;

    StaffSessionFilter(AuthenticateStaffSessionQuery sessions, ObjectMapper json, boolean requireStaffSession) {
        this.sessions = sessions;
        this.json = json;
        this.requireStaffSession = requireStaffSession;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TerminalPrincipal device)) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader(STAFF_SESSION_HEADER);
        if (token == null) {
            if (requireStaffSession && UI_DEVICES.contains(device.deviceRole()) && !signInRequest(request)) {
                ProblemResponses.write(response, json, HttpStatus.UNAUTHORIZED, "STAFF_SESSION_REQUIRED",
                        "Sign in with your username and PIN first");
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        Optional<Staff> staff = sessions.authenticate(token.trim(), device.terminalId());
        Role effective = staff.map(s -> s.role().effectiveRole(device.deviceRole())).orElse(null);
        if (effective == null) {
            ProblemResponses.write(response, json, HttpStatus.UNAUTHORIZED, "STAFF_SESSION_INVALID",
                    "Signed out or session expired; sign in again");
            return;
        }
        Staff s = staff.get();
        TerminalPrincipal principal = new TerminalPrincipal(device.terminalId(), device.name(), effective,
                device.deviceRole(), new TerminalPrincipal.Staff(s.id(), s.username(), s.displayName(), s.role()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + effective.name()))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    /** The calls a device makes before anyone is signed in on it. */
    private static boolean signInRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        return HttpMethod.GET.matches(method) && (path.equals("/api/v1/me") || path.equals("/api/v1/staff/roster"))
                || HttpMethod.POST.matches(method) && path.equals("/api/v1/staff-sessions")
                || !path.startsWith("/api/");
    }
}
