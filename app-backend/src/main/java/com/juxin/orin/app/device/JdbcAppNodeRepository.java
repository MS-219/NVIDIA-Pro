package com.juxin.orin.app.device;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAppNodeRepository implements AppNodeRepository {
    private final JdbcTemplate jdbc;

    public JdbcAppNodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AppNode> findOwnedBy(long ownerUserId) {
        return jdbc.query(selectColumns() + " WHERE owner_user_id = ? ORDER BY id ASC",
                this::mapNode, ownerUserId);
    }

    @Override
    public Optional<AppNode> findById(long id) {
        return jdbc.query(selectColumns() + " WHERE id = ? LIMIT 1", this::mapNode, id)
                .stream().findFirst();
    }

    @Override
    public Optional<AppNode> findByCode(String code) {
        // The APP normalizes codes before this call. UPPER also handles rows
        // provisioned manually with a different case on case-sensitive H2.
        return jdbc.query(selectColumns() + " WHERE UPPER(binding_code) = UPPER(?) LIMIT 1",
                this::mapNode, code).stream().findFirst();
    }

    @Override
    public boolean claim(long id, long ownerUserId, String name, Instant boundAt) {
        int updated = jdbc.update("""
                UPDATE app_node
                   SET owner_user_id = ?, name = ?, bound_at = ?, updated_at = ?
                 WHERE id = ? AND owner_user_id IS NULL
                """, ownerUserId, name, timestamp(boundAt), timestamp(boundAt), id);
        return updated == 1;
    }

    @Override
    public boolean release(long id, long ownerUserId, Instant releasedAt) {
        int updated = jdbc.update("""
                UPDATE app_node
                   SET owner_user_id = NULL, bound_at = NULL, updated_at = ?
                 WHERE id = ? AND owner_user_id = ?
                """, timestamp(releasedAt), id, ownerUserId);
        return updated == 1;
    }

    @Override
    public DashboardAggregate aggregateOwnedBy(long ownerUserId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(CASE WHEN LOWER(status) = 'online' THEN 1 ELSE 0 END), 0) AS online,
                       COALESCE(SUM(hashrate), 0) AS total_hashrate,
                       COALESCE(SUM(daily_earnings), 0) AS today_earnings,
                       COALESCE(SUM(total_earnings), 0) AS total_earnings
                  FROM app_node
                 WHERE owner_user_id = ?
                """, (rs, rowNum) -> new DashboardAggregate(
                rs.getLong("total"),
                rs.getLong("online"),
                decimal(rs.getBigDecimal("total_hashrate")),
                decimal(rs.getBigDecimal("today_earnings")),
                decimal(rs.getBigDecimal("total_earnings"))), ownerUserId);
    }

    @Override
    public List<EarningSnapshot> earningsOwnedBy(long ownerUserId) {
        return jdbc.query("""
                SELECT id, name, daily_earnings, total_earnings, updated_at
                  FROM app_node
                 WHERE owner_user_id = ?
                   AND (daily_earnings <> 0 OR total_earnings <> 0)
                 ORDER BY updated_at DESC, id ASC
                """, (rs, rowNum) -> new EarningSnapshot(
                rs.getLong("id"),
                rs.getString("name"),
                decimal(rs.getBigDecimal("daily_earnings")),
                decimal(rs.getBigDecimal("total_earnings")),
                instant(rs.getTimestamp("updated_at"))), ownerUserId);
    }

    private static String selectColumns() {
        return """
                SELECT id, binding_code, owner_user_id, name, status, hashrate,
                       temperature, daily_earnings, total_earnings, last_reported_at,
                       bound_at, created_at, updated_at
                  FROM app_node
                """;
    }

    private AppNode mapNode(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AppNode(
                rs.getLong("id"),
                rs.getString("binding_code"),
                nullableLong(rs, "owner_user_id"),
                rs.getString("name"),
                rs.getString("status"),
                decimal(rs.getBigDecimal("hashrate")),
                decimalNullable(rs.getBigDecimal("temperature")),
                decimal(rs.getBigDecimal("daily_earnings")),
                decimal(rs.getBigDecimal("total_earnings")),
                instant(rs.getTimestamp("last_reported_at")),
                instant(rs.getTimestamp("bound_at")),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal decimalNullable(BigDecimal value) {
        return value;
    }
}
