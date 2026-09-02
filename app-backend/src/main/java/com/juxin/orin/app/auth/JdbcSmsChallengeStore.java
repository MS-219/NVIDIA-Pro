package com.juxin.orin.app.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcSmsChallengeStore implements SmsChallengeStore {
    private final JdbcTemplate jdbc;

    public JdbcSmsChallengeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean sentRecently(String phone, String clientIp, Instant since) {
        Integer phoneCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sms_login_challenge WHERE phone = ? AND created_at >= ?",
                Integer.class, phone, Timestamp.from(since));
        if (phoneCount != null && phoneCount > 0) {
            return true;
        }
        Integer ipCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sms_login_challenge WHERE client_ip = ? AND created_at >= ?",
                Integer.class, clientIp, Timestamp.from(since));
        return ipCount != null && ipCount >= 5;
    }

    @Override
    public void save(String phone, String codeHash, Instant expiresAt, String clientIp, String requestId,
                     String providerRequestId, Instant createdAt) {
        jdbc.update("""
                INSERT INTO sms_login_challenge
                    (phone, code_hash, expires_at, client_ip, request_id, provider_request_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, phone, codeHash, Timestamp.from(expiresAt), clientIp, requestId, providerRequestId,
                Timestamp.from(createdAt));
    }

    @Override
    public Optional<Challenge> findLatestActive(String phone, Instant now) {
        List<Challenge> rows = jdbc.query("""
                SELECT id, phone, code_hash, expires_at, attempts
                FROM sms_login_challenge
                WHERE phone = ? AND consumed_at IS NULL AND expires_at > ?
                ORDER BY id DESC LIMIT 1
                """, (rs, rowNum) -> new Challenge(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("code_hash"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getInt("attempts")), phone, Timestamp.from(now));
        return rows.stream().findFirst();
    }

    @Override
    public boolean consume(long id, Instant consumedAt) {
        return jdbc.update("UPDATE sms_login_challenge SET consumed_at = ? "
                + "WHERE id = ? AND consumed_at IS NULL", Timestamp.from(consumedAt), id) == 1;
    }

    @Override
    // A failed login rolls back its outer transaction. Keep the failed-attempt
    // counter in its own transaction so brute-force protection cannot be reset
    // by that rollback.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementAttempts(long id) {
        jdbc.update("UPDATE sms_login_challenge SET attempts = attempts + 1 WHERE id = ?", id);
    }
}
