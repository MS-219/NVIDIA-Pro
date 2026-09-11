package com.juxin.orin.app.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Mirrors settled daily earnings ({@code device_earnings} in the 二开后台) into
 * the APP wallet ledger ({@code app_wallet_ledger}) as idempotent credit
 * entries, so the customer sees a real balance and can withdraw.
 *
 * <p>The 二开后台 is the source of truth for settlement amounts; the APP wallet
 * only reflects those amounts.  Each earning row is keyed by its primary key,
 * so re-running the sync is safe and never double-credits.</p>
 */
@Service
public class WalletSyncService {
    private static final Logger log = LoggerFactory.getLogger(WalletSyncService.class);

    private final JdbcTemplate appJdbc;
    private final JdbcTemplate nodeJdbc;
    private final boolean enabled;

    public WalletSyncService(
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

    /** Synchronize every settled earning for one APP account into its wallet. */
    public void syncForUser(long appUserId) {
        if (!enabled || appUserId <= 0) return;
        try {
            String phone = appJdbc.query("SELECT phone FROM app_user_account WHERE id = ? LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, appUserId);
            if (phone == null || phone.isBlank()) return;
            nodeJdbc.query("""
                    SELECT de.id AS earning_id, de.amount, de.date AS earning_date
                      FROM device_earnings de
                      JOIN app_user u ON u.id = de.user_id
                     WHERE u.phone = ? AND (u.deleted = 0 OR u.deleted IS NULL)
                     ORDER BY de.id ASC
                    """, rs -> {
                long earningId = rs.getLong("earning_id");
                BigDecimal amount = rs.getBigDecimal("amount");
                java.sql.Date d = rs.getDate("earning_date");
                LocalDate date = d == null ? null : d.toLocalDate();
                credit(appUserId, earningId, amount, date);
            }, phone);
        } catch (RuntimeException error) {
            // Legacy DB is optional; APP requests must keep working when it is unavailable.
            log.warn("wallet earnings sync failed for APP user {}", appUserId, error);
        }
    }

    /** Background sweep so balances stay current even before the user opens the wallet. */
    @Scheduled(fixedRate = 60000, initialDelay = 15000)
    public void syncAll() {
        if (!enabled) return;
        try {
            List<Long> ids = appJdbc.query("SELECT id FROM app_user_account", (rs, i) -> rs.getLong(1));
            for (long id : ids) {
                syncForUser(id);
            }
        } catch (RuntimeException error) {
            log.warn("wallet scheduled sync failed", error);
        }
    }

    private void credit(long appUserId, long earningId, BigDecimal amount, LocalDate date) {
        if (amount == null || amount.signum() <= 0) return;
        String key = "earn:" + earningId;
        Long exists = appJdbc.queryForObject(
                "SELECT COUNT(*) FROM app_wallet_ledger WHERE idempotency_key = ?", Long.class, key);
        if (exists != null && exists > 0) return;
        BigDecimal current = currentBalance(appUserId);
        BigDecimal after = current.add(amount);
        try {
            appJdbc.update("""
                    INSERT INTO app_wallet_ledger
                        (user_id, amount, balance_after, direction, entry_type, idempotency_key, description)
                    VALUES (?, ?, ?, 'credit', 'earnings', ?, ?)
                    """, appUserId, amount, after, key,
                    "设备收益结算 " + (date == null ? "" : date.toString()));
        } catch (DuplicateKeyException duplicate) {
            // A concurrent sweep already inserted this earning.
        }
    }

    private BigDecimal currentBalance(long appUserId) {
        Object v = appJdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN direction='credit' THEN amount ELSE -amount END),0) FROM app_wallet_ledger WHERE user_id=?",
                Object.class, appUserId);
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}
