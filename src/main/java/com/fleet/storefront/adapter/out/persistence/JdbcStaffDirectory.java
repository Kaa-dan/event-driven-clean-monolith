package com.fleet.storefront.adapter.out.persistence;

import com.fleet.storefront.application.port.out.StaffDirectory;
import com.fleet.storefront.domain.order.TerminalId;
import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.staff.StaffExistsException;
import com.fleet.storefront.domain.staff.StaffId;
import com.fleet.storefront.domain.staff.StaffRole;
import com.fleet.storefront.domain.staff.StaffSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcStaffDirectory implements StaffDirectory {

    private static final String COLUMNS =
            "id, username, display_name, role, active, failed_attempts, locked_until, created_at";

    private static final RowMapper<Staff> STAFF = (rs, n) -> new Staff(
            new StaffId(rs.getObject("id", UUID.class)), rs.getString("username"), rs.getString("display_name"),
            StaffRole.valueOf(rs.getString("role")), rs.getBoolean("active"), rs.getInt("failed_attempts"),
            instant(rs.getTimestamp("locked_until")), rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<StaffSession> SESSION = (rs, n) -> new StaffSession(
            rs.getObject("id", UUID.class), new StaffId(rs.getObject("staff_id", UUID.class)),
            new TerminalId(rs.getString("terminal_id")), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(), instant(rs.getTimestamp("ended_at")));

    private final JdbcClient jdbc;

    JdbcStaffDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Timestamp timestamp(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    @Override
    public Optional<Staff> findById(StaffId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM staff WHERE id = :id")
                .param("id", id.value())
                .query(STAFF)
                .optional();
    }

    @Override
    public Optional<Staff> findByUsername(String username) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM staff WHERE username = :username")
                .param("username", username)
                .query(STAFF)
                .optional();
    }

    @Override
    public Optional<StaffCredentials> findCredentialsForUpdate(String username) {
        return jdbc.sql("SELECT " + COLUMNS + ", pin_hash FROM staff WHERE username = :username FOR UPDATE")
                .param("username", username)
                .query((rs, n) -> new StaffCredentials(STAFF.mapRow(rs, n), rs.getString("pin_hash")))
                .optional();
    }

    @Override
    public List<Staff> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM staff ORDER BY display_name, username").query(STAFF).list();
    }

    @Override
    public void insert(Staff staff, String pinHash) {
        try {
            jdbc.sql("""
                            INSERT INTO staff (id, username, display_name, role, pin_hash, active, failed_attempts,
                                               locked_until, created_at)
                            VALUES (:id, :username, :name, :role, :pin, :active, :failed, :locked, :created)
                            """)
                    .param("id", staff.id().value())
                    .param("username", staff.username())
                    .param("name", staff.displayName())
                    .param("role", staff.role().name())
                    .param("pin", pinHash)
                    .param("active", staff.active())
                    .param("failed", staff.failedAttempts())
                    .param("locked", timestamp(staff.lockedUntil()))
                    .param("created", Timestamp.from(staff.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            // Lost a race with a concurrent creation of the same username.
            throw new StaffExistsException(staff.username());
        }
    }

    @Override
    public void update(Staff staff) {
        jdbc.sql("""
                        UPDATE staff SET display_name = :name, role = :role, active = :active,
                                         failed_attempts = :failed, locked_until = :locked
                         WHERE id = :id
                        """)
                .param("id", staff.id().value())
                .param("name", staff.displayName())
                .param("role", staff.role().name())
                .param("active", staff.active())
                .param("failed", staff.failedAttempts())
                .param("locked", timestamp(staff.lockedUntil()))
                .update();
    }

    @Override
    public void updatePin(StaffId id, String pinHash) {
        jdbc.sql("UPDATE staff SET pin_hash = :pin WHERE id = :id")
                .param("id", id.value())
                .param("pin", pinHash)
                .update();
    }

    @Override
    public long countActiveForUpdate(StaffRole role) {
        return jdbc.sql("SELECT id FROM staff WHERE role = :role AND active FOR UPDATE")
                .param("role", role.name())
                .query(UUID.class)
                .list()
                .size();
    }

    @Override
    public void insertSession(StaffSession session, String tokenHash) {
        jdbc.sql("""
                        INSERT INTO staff_session (id, token_hash, staff_id, terminal_id, created_at, expires_at)
                        VALUES (:id, :hash, :staff, :terminal, :created, :expires)
                        """)
                .param("id", session.id())
                .param("hash", tokenHash)
                .param("staff", session.staffId().value())
                .param("terminal", session.terminalId().value())
                .param("created", Timestamp.from(session.createdAt()))
                .param("expires", Timestamp.from(session.expiresAt()))
                .update();
    }

    @Override
    public Optional<StaffSession> findSessionByTokenHash(String tokenHash) {
        return jdbc.sql("""
                        SELECT id, staff_id, terminal_id, created_at, expires_at, ended_at
                          FROM staff_session WHERE token_hash = :hash
                        """)
                .param("hash", tokenHash)
                .query(SESSION)
                .optional();
    }

    @Override
    public void endSession(String tokenHash, Instant at) {
        jdbc.sql("UPDATE staff_session SET ended_at = :at WHERE token_hash = :hash AND ended_at IS NULL")
                .param("hash", tokenHash)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public void endSessionsOf(StaffId id, Instant at) {
        jdbc.sql("UPDATE staff_session SET ended_at = :at WHERE staff_id = :staff AND ended_at IS NULL")
                .param("staff", id.value())
                .param("at", Timestamp.from(at))
                .update();
    }
}
