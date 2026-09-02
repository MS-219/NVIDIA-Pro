package com.juxin.orin.app.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {
    private final JdbcTemplate jdbc;

    public JdbcUserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> findByPhone(String phone) {
        List<UserAccount> rows = jdbc.query("""
                SELECT id, phone, nickname, created_at
                FROM app_user_account WHERE phone = ? AND status = 1 LIMIT 1
                """, (rs, rowNum) -> new UserAccount(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getTimestamp("created_at").toInstant()), phone);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        List<UserAccount> rows = jdbc.query("""
                SELECT id, phone, nickname, created_at
                FROM app_user_account WHERE id = ? AND status = 1 LIMIT 1
                """, (rs, rowNum) -> new UserAccount(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getTimestamp("created_at").toInstant()), id);
        return rows.stream().findFirst();
    }

    @Override
    public UserAccount create(String phone, String nickname) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO app_user_account (phone, nickname, status, created_at, updated_at)
                        VALUES (?, ?, 1, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, phone);
                statement.setString(2, nickname);
                statement.setTimestamp(3, Timestamp.from(now));
                statement.setTimestamp(4, Timestamp.from(now));
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException duplicate) {
            return findByPhone(phone).orElseThrow(() -> duplicate);
        }
        // Some JDBC drivers (notably H2) return the generated id together with
        // other generated columns. Select the id explicitly instead of relying
        // on GeneratedKeyHolder#getKey(), which only accepts a single column.
        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new IllegalStateException("创建用户后未返回用户ID");
        }
        return new UserAccount(key.longValue(), phone, nickname, now);
    }

    @Override
    public Optional<UserAccount> updateNickname(long id, String nickname) {
        int updated = jdbc.update("UPDATE app_user_account SET nickname = ?, updated_at = ? WHERE id = ? AND status = 1",
                nickname, Timestamp.from(Instant.now()), id);
        return updated == 1 ? findById(id) : Optional.empty();
    }

    private static Number generatedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number number) {
                return number;
            }
        }
        return keys.values().stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .findFirst()
                .orElse(null);
    }
}
