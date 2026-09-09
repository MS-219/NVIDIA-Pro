package com.juxin.orin.app.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Mirrors the 二开后台 (juxin_node) device table into the APP node table.
 *
 * <p>The 二开后台 is the single source of truth for device binding codes.  This
 * service mirrors <em>every</em> device type (physical RK3588 nodes and legacy
 * virtual devices), not only virtual devices, so a physical node whose binding
 * code is shown on the device screen and in the 二开后台 can always be bound
 * from the APP.</p>
 */
@Service
public class NodeDeviceSyncService {
    private static final Logger log = LoggerFactory.getLogger(NodeDeviceSyncService.class);

    private final JdbcTemplate appJdbc;
    private final JdbcTemplate nodeJdbc;
    private final boolean enabled;

    public NodeDeviceSyncService(
            JdbcTemplate appJdbc,
            @Value("${app.node-db.url:}") String url,
            @Value("${app.node-db.username:}") String username,
            @Value("${app.node-db.password:}") String password,
            @Value("${app.node-db.driver-class-name:com.mysql.cj.jdbc.Driver}") String driverClassName) {
        this.appJdbc = appJdbc;
        this.enabled = url != null && !url.isBlank() && username != null && !username.isBlank();
        if (enabled) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
            if (driverClassName != null && !driverClassName.isBlank()) {
                dataSource.setDriverClassName(driverClassName);
            }
            this.nodeJdbc = new JdbcTemplate(dataSource);
        } else {
            this.nodeJdbc = null;
        }
    }

    /** Synchronize every legacy device assigned to the APP account. */
    public void syncForUser(long appUserId) {
        if (!enabled || appUserId <= 0) return;
        try {
            String phone = appJdbc.query("SELECT phone FROM app_user_account WHERE id = ? LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, appUserId);
            if (phone == null || phone.isBlank()) return;
            List<LegacyDevice> devices = nodeJdbc.query("""
                    SELECT d.id, d.bind_code, d.name, d.status, d.hashrate,
                           d.last_heartbeat_time, d.user_id, d.type, d.bind_time
                      FROM device d
                      JOIN app_user u ON u.id = d.user_id
                     WHERE d.user_id IS NOT NULL
                       AND u.phone = ? AND (u.deleted = 0 OR u.deleted IS NULL)
                     ORDER BY d.id ASC
                    """, this::mapLegacyDevice, phone);
            for (LegacyDevice device : devices) {
                upsert(device, resolveAppOwner(device));
            }
            releaseStaleForUser(appUserId, devices);
        } catch (RuntimeException error) {
            // Legacy DB is optional; APP requests must keep working when it is unavailable.
            log.warn("legacy device sync failed for APP user {}", appUserId, error);
        }
    }

    /**
     * Resolve a binding code against the 二开后台 (the primary data source) and
     * mirror the device into the APP node table so {@code bind} can claim it.
     *
     * <p>Returns {@code null} when the legacy DB is unavailable or the code is
     * unknown.  The returned owner is the APP account derived from the legacy
     * device owner's phone; it is {@code null} when the legacy device has no
     * owner yet or its owner has no APP account.</p>
     */
    public LegacyLookup syncByCode(String rawCode) {
        if (!enabled) return null;
        String code = normalizeRawCode(rawCode);
        if (code == null) return null;
        try {
            LegacyDevice device = nodeJdbc.query("""
                    SELECT d.id, d.bind_code, d.name, d.status, d.hashrate,
                           d.last_heartbeat_time, d.user_id, d.type, d.bind_time
                      FROM device d
                     WHERE UPPER(d.bind_code) = UPPER(?)
                     LIMIT 1
                    """, rs -> rs.next() ? mapLegacyDevice(rs, 0) : null, code);
            if (device == null) return null;
            Long appOwner = resolveAppOwner(device);
            LegacyLookup lookup = new LegacyLookup(device, appOwner);
            if (device.legacyUserId() != null && appOwner == null) {
                // Bound in the 二开后台 to a legacy account that has no APP
                // account; leave app_node untouched so bind() can reject it as
                // taken instead of silently clearing an existing owner.
                return lookup;
            }
            upsert(device, appOwner);
            return lookup;
        } catch (RuntimeException error) {
            log.warn("legacy device sync-by-code failed for {}", code, error);
            return null;
        }
    }

    /**
     * Reflect an APP-side claim back into the 二开后台 so both systems keep the
     * same owner.  Only devices that are still unbound (or bound to the same
     * legacy account) are touched.
     */
    public void recordBindBack(LegacyDevice device, long appUserId) {
        if (!enabled || device == null) return;
        Long legacyUserId = legacyUserIdByAppAccount(appUserId);
        if (legacyUserId == null) {
            log.warn("APP bind-back skipped: no legacy user for APP account {}", appUserId);
            return;
        }
        try {
            nodeJdbc.update("""
                    UPDATE device
                       SET user_id = ?, bind_time = COALESCE(bind_time, NOW())
                     WHERE id = ? AND (user_id IS NULL OR user_id = ?)
                    """, legacyUserId, device.id(), legacyUserId);
        } catch (RuntimeException error) {
            log.warn("legacy device bind-back failed for device {}", device.id(), error);
        }
    }

    /** Release a legacy device binding when the APP user unbinds the node. */
    public void releaseLegacy(String code, long appUserId) {
        if (!enabled || code == null) return;
        Long legacyUserId = legacyUserIdByAppAccount(appUserId);
        if (legacyUserId == null) return;
        try {
            nodeJdbc.update("""
                    UPDATE device
                       SET user_id = NULL
                     WHERE UPPER(bind_code) = UPPER(?) AND user_id = ?
                    """, code.trim(), legacyUserId);
        } catch (RuntimeException error) {
            log.warn("legacy device release failed for code {}", code, error);
        }
    }

    private void upsert(LegacyDevice device, Long appOwnerUserId) {
        String code = normalizeBindCode(device);
        String name = normalizeName(device);
        String status = device.status() == 1 ? "online" : "offline";
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp boundAt = appOwnerUserId == null ? null : now;
        if (rowExists(code)) {
            // The 二开后台 is authoritative: always apply the resolved owner so
            // a rebind (or unbind) there is reflected here, even when the APP
            // node was previously owned by a different account.
            updateRow(code, appOwnerUserId, name, status, device.hashrate(), device.lastHeartbeat(), boundAt, now);
            return;
        }
        try {
            appJdbc.update("""
                    INSERT INTO app_node
                        (binding_code, owner_user_id, name, status, hashrate,
                         last_reported_at, bound_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, code, appOwnerUserId, name, status, device.hashrate(),
                    device.lastHeartbeat(), boundAt, now, now);
        } catch (DuplicateKeyException duplicate) {
            // A concurrent request may have inserted the same binding code.
            updateRow(code, appOwnerUserId, name, status, device.hashrate(), device.lastHeartbeat(), boundAt, now);
        }
    }

    private void updateRow(String code, Long appOwnerUserId, String name, String status,
                           int hashrate, Timestamp lastHeartbeat, Timestamp boundAt, Timestamp now) {
        appJdbc.update("""
                UPDATE app_node
                   SET owner_user_id = ?, name = ?, status = ?, hashrate = ?,
                       last_reported_at = ?, bound_at = COALESCE(bound_at, ?), updated_at = ?
                 WHERE UPPER(binding_code) = UPPER(?)
                """, appOwnerUserId, name, status, hashrate, lastHeartbeat, boundAt, now, code);
    }

    /**
     * Release APP nodes owned by {@code appUserId} whose code is no longer
     * assigned to that user in the 二开后台 (rebound to another account, unbound,
     * or deleted there).  This keeps the APP mirror exactly in line with the
     * authoritative backend.
     */
    private void releaseStaleForUser(long appUserId, List<LegacyDevice> devices) {
        Set<String> authoritative = new HashSet<>();
        for (LegacyDevice device : devices) {
            authoritative.add(normalizeBindCode(device));
        }
        List<String> owned = appJdbc.query(
                "SELECT binding_code FROM app_node WHERE owner_user_id = ?",
                (rs, i) -> rs.getString(1), appUserId);
        for (String raw : owned) {
            String code = normalizeRawCode(raw);
            if (code == null || authoritative.contains(code)) continue;
            appJdbc.update("""
                    UPDATE app_node
                       SET owner_user_id = NULL, bound_at = NULL
                     WHERE owner_user_id = ? AND UPPER(binding_code) = UPPER(?)
                    """, appUserId, code);
        }
    }

    private Long resolveAppOwner(LegacyDevice device) {
        if (device.legacyUserId() == null) return null;
        String phone = nodeJdbc.query("SELECT phone FROM app_user WHERE id = ? LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, device.legacyUserId());
        return appAccountIdByPhone(phone);
    }

    private Long legacyUserIdByAppAccount(long appUserId) {
        String phone = appJdbc.query("SELECT phone FROM app_user_account WHERE id = ? LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, appUserId);
        if (phone == null || phone.isBlank()) return null;
        return nodeJdbc.query("SELECT id FROM app_user WHERE phone = ? LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, phone);
    }

    private Long appAccountIdByPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return appJdbc.query("SELECT id FROM app_user_account WHERE phone = ? LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, phone);
    }

    private boolean rowExists(String code) {
        return appJdbc.queryForObject(
                "SELECT COUNT(*) FROM app_node WHERE binding_code = ?", Long.class, code) > 0;
    }

    private LegacyDevice mapLegacyDevice(ResultSet rs, int rowNum) throws SQLException {
        long userId = rs.getLong("user_id");
        Long legacyUserId = rs.wasNull() ? null : userId;
        return new LegacyDevice(
                rs.getLong("id"), rs.getString("bind_code"), rs.getString("name"),
                rs.getInt("status"), rs.getInt("hashrate"), rs.getTimestamp("last_heartbeat_time"),
                legacyUserId, rs.getInt("type"), rs.getTimestamp("bind_time"));
    }

    private static String normalizeRawCode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeBindCode(LegacyDevice device) {
        String code = device.bindingCode();
        if (code == null || code.isBlank()) code = "VD-" + device.id();
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeName(LegacyDevice device) {
        String name = device.name();
        if (name == null || name.isBlank()) {
            // Match the 二开后台 device-type labels (实体设备 / 挂靠设备 / 聚芯节点).
            name = switch (device.type()) {
                case 0 -> "实体设备";
                case 1 -> "挂靠设备";
                default -> "聚芯节点";
            };
        }
        name = name.trim();
        if (name.length() > 80) name = name.substring(0, 80);
        return name;
    }

    /** A legacy device plus the APP account its legacy owner maps to (may be null). */
    public record LegacyLookup(LegacyDevice device, Long appOwnerUserId) {}

    public record LegacyDevice(long id, String bindingCode, String name, int status,
                               int hashrate, Timestamp lastHeartbeat, Long legacyUserId,
                               int type, Timestamp bindTime) {}
}
