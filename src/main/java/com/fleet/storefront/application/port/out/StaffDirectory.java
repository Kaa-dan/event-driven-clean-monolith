package com.fleet.storefront.application.port.out;

import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.staff.StaffId;
import com.fleet.storefront.domain.staff.StaffRole;
import com.fleet.storefront.domain.staff.StaffSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Staff accounts, their PIN hashes and their sign-in sessions. */
public interface StaffDirectory {

    Optional<Staff> findById(StaffId id);

    Optional<Staff> findByUsername(String username);

    /** The account with its PIN hash, locked until commit (serializes concurrent sign-ins of one person). */
    Optional<StaffCredentials> findCredentialsForUpdate(String username);

    List<Staff> findAll();

    void insert(Staff staff, String pinHash);

    /** Saves name, role, active flag and the failed-PIN state. */
    void update(Staff staff);

    void updatePin(StaffId id, String pinHash);

    /** Counts active staff of {@code role}, locking them until commit (serializes concurrent demotions). */
    long countActiveForUpdate(StaffRole role);

    void insertSession(StaffSession session, String tokenHash);

    Optional<StaffSession> findSessionByTokenHash(String tokenHash);

    /** Ends the session (idempotent). */
    void endSession(String tokenHash, Instant at);

    /** Ends every open session of the person, e.g. after a PIN reset or deactivation. */
    void endSessionsOf(StaffId id, Instant at);

    record StaffCredentials(Staff staff, String pinHash) {
    }
}
