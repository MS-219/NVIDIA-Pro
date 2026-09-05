package com.juxin.orin.app.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

/** Keeps the legacy二开后台 user list's matching phone record current. */
@Service
public class NodeUserSyncService {
    private static final Logger log = LoggerFactory.getLogger(NodeUserSyncService.class);
    private final JdbcTemplate nodeJdbc;
    private final boolean enabled;

    public NodeUserSyncService(
            @Value("${app.node-db.url:}") String url,
            @Value("${app.node-db.username:}") String username,
            @Value("${app.node-db.password:}") String password) {
        this.enabled = url != null && !url.isBlank() && username != null && !username.isBlank();
        if (enabled) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            this.nodeJdbc = new JdbcTemplate(dataSource);
        } else {
            this.nodeJdbc = null;
        }
    }

    public void syncNickname(String phone, String nickname) {
        if (!enabled || phone == null || phone.isBlank() || nickname == null) return;
        try {
            int changed = nodeJdbc.update("UPDATE app_user SET nickname = ? WHERE phone = ? AND merchant_id IS NULL", nickname, phone);
            if (changed == 0) {
                // APP accounts use a separate ID space; keep a stable synthetic row for the legacy list.
                long id = 900_000_000L + Math.abs((long) phone.hashCode());
                nodeJdbc.update("""
                        INSERT INTO app_user (id, openid, nickname, phone, user_type, deleted, merchant_id)
                        VALUES (?, ?, ?, ?, 'personal', 0, NULL)
                        ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), phone = VALUES(phone), deleted = 0
                        """, id, "sms:" + phone, nickname, phone);
            }
        } catch (RuntimeException error) {
            // APP profile updates must remain available if the optional legacy DB is down.
            log.warn("legacy user nickname sync failed for phone suffix {}", phone.substring(Math.max(0, phone.length() - 4)), error);
        }
    }
}
