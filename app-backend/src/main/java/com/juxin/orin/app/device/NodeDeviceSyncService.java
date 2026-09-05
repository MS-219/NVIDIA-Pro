package com.juxin.orin.app.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Mirrors virtual devices created by the legacy 二开后台 into the APP node table. */
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
            @Value("${app.node-db.password:}") String password) {
        this.appJdbc = appJdbc;
        this.enabled = url != null && !url.isBlank() && username != null && !username.isBlank();
        if (enabled) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            this.nodeJdbc = new JdbcTemplate(dataSource);
        } else {
            this.nodeJdbc = null;
        }
    }

    /** Synchronize all legacy virtual devices assigned to the APP account. */
    public void syncForUser(long appUserId) {
        if (!enabled || appUserId <= 0) return;
        try {
            String phone = appJdbc.query("SELECT phone FROM app_user_account WHERE id = ? LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, appUserId);
            if (phone == null || phone.isBlank()) return;
            List<LegacyDevice> devices = nodeJdbc.query("""
                    SELECT d.id, d.bind_code, d.name, d.status, d.hashrate,
                           d.last_heartbeat_time, d.user_id
                      FROM device d
                      JOIN app_user u ON u.id = d.user_id
                     WHERE d.type = 1 AND d.user_id IS NOT NULL
                       AND u.phone = ? AND (u.deleted = 0 OR u.deleted IS NULL)
                     ORDER BY d.id ASC
                    """, (rs, rowNum) -> new LegacyDevice(
                    rs.getLong("id"), rs.getString("bind_code"), rs.getString("name"),
                    rs.getInt("status"), rs.getInt("hashrate"),
                    rs.getTimestamp("last_heartbeat_time"), rs.getLong("user_id")), phone);
            for (LegacyDevice device : devices) upsert(appUserId, device);
        } catch (RuntimeException error) {
            // Legacy DB is optional; APP requests must keep working when it is unavailable.
            log.warn("legacy virtual device sync failed for APP user {}", appUserId, error);
        }
    }

    private void upsert(long appUserId, LegacyDevice device) {
        String code = device.bindingCode;
        if (code == null || code.isBlank()) code = "VD-" + device.id;
        code = code.trim().toUpperCase(Locale.ROOT);
        String name = device.name == null || device.name.isBlank() ? "虚拟设备" : device.name.trim();
        if (name.length() > 80) name = name.substring(0, 80);
        String status = device.status == 1 ? "online" : "offline";
        Timestamp now = Timestamp.from(Instant.now());
        Long existingOwner = appJdbc.query("SELECT owner_user_id FROM app_node WHERE binding_code = ? LIMIT 1",
                rs -> rs.next() && rs.getObject(1) != null ? rs.getLong(1) : null, code);
        if (existingOwner != null && existingOwner != appUserId) {
            // Never let a legacy binding-code collision reassign an APP node
            // that is already owned by another account.
            return;
        }
        if (existingOwner != null || appJdbc.queryForObject(
                "SELECT COUNT(*) FROM app_node WHERE binding_code = ?", Long.class, code) > 0) {
            appJdbc.update("""
                    UPDATE app_node
                       SET owner_user_id = ?, name = ?, status = ?, hashrate = ?,
                           last_reported_at = ?, bound_at = COALESCE(bound_at, ?), updated_at = ?
                     WHERE binding_code = ?
                    """, appUserId, name, status, device.hashrate,
                    device.lastHeartbeat, now, now, code);
            return;
        }
        try {
            appJdbc.update("""
                    INSERT INTO app_node
                        (binding_code, owner_user_id, name, status, hashrate,
                         last_reported_at, bound_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, code, appUserId, name, status, device.hashrate,
                    device.lastHeartbeat, now, now, now);
        } catch (RuntimeException duplicate) {
            // A concurrent request may have inserted the same binding code.
            Long ownerAfterRace = appJdbc.query("SELECT owner_user_id FROM app_node WHERE binding_code = ? LIMIT 1",
                    rs -> rs.next() && rs.getObject(1) != null ? rs.getLong(1) : null, code);
            if (ownerAfterRace == null || ownerAfterRace == appUserId) {
                appJdbc.update("""
                        UPDATE app_node
                           SET owner_user_id = ?, name = ?, status = ?, hashrate = ?,
                               last_reported_at = ?, bound_at = COALESCE(bound_at, ?), updated_at = ?
                         WHERE binding_code = ?
                        """, appUserId, name, status, device.hashrate,
                        device.lastHeartbeat, now, now, code);
            }
        }
    }

    private record LegacyDevice(long id, String bindingCode, String name, int status,
                                int hashrate, Timestamp lastHeartbeat, long legacyUserId) {}
}
