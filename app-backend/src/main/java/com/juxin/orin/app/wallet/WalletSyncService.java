package com.juxin.orin.app.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final JdbcTemplate appJdbc;
    private final JdbcTemplate nodeJdbc;
    private final boolean enabled;
    private final String nodeBackendUrl;

    public WalletSyncService(
            JdbcTemplate appJdbc,
            @Value("${app.node-db.url:}") String url,
            @Value("${app.node-db.username:}") String username,
            @Value("${app.node-db.password:}") String password,
            @Value("${app.node-db.driver-class-name:com.mysql.cj.jdbc.Driver}") String driverClassName,
            @Value("${app.node-backend.url:http://juxin-node-backend:8090}") String nodeBackendUrl) {
        this.appJdbc = appJdbc;
        this.nodeBackendUrl = nodeBackendUrl;
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

    /** Wallet balance for one user (source of truth: 二开后台 app_user.balance). */
    public BigDecimal getWalletBalance(long appUserId) {
        if (!enabled || appUserId <= 0) return BigDecimal.ZERO;
        try {
            Long uid = nodeUserId(appUserId);
            if (uid == null) return BigDecimal.ZERO;
            Object v = nodeJdbc.queryForObject("SELECT balance FROM app_user WHERE id=?", Object.class, uid);
            return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
        } catch (RuntimeException error) {
            log.warn("read node balance failed for APP user {}", appUserId, error);
            return BigDecimal.ZERO;
        }
    }

    /** Map an APP account to the 二开后台 user id by phone number. */
    private Long nodeUserId(long appUserId) {
        String phone = appJdbc.query("SELECT phone FROM app_user_account WHERE id=? LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, appUserId);
        if (phone == null || phone.isBlank()) return null;
        return nodeJdbc.query("SELECT id FROM app_user WHERE phone=? AND (deleted=0 OR deleted IS NULL) LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, phone);
    }

    /**
     * Submit a withdrawal into the 二开后台 (deduct {@code app_user.balance} + quota, insert a
     * {@code withdraw} row) so the existing admin 提现管理 sees it directly.
     *
     * @return error message, or {@code null} on success.
     */
    public String applyWithdraw(long appUserId, BigDecimal amount, int type, String account, String realName, String qrCode) {
        if (!enabled || appUserId <= 0) return "提现服务暂不可用";
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) return "提现金额最低为 0.01 元";
        try {
            Long uid = nodeUserId(appUserId);
            if (uid == null) return "用户未同步到后台，请稍后再试";
            // 调用二开后台 /apply，由二开后台统一执行提现日/签约/余额等规则，保证两套系统规则一致。
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", uid);
            payload.put("amount", amount);
            payload.put("type", type);
            payload.put("account", account);
            payload.put("realName", realName);
            payload.put("qrCode", qrCode);
            String json = OBJECT_MAPPER.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(nodeBackendUrl + "/api/withdraw/apply"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = OBJECT_MAPPER.readTree(response.body());
            if (node.path("code").asInt(-1) == 200) return null;
            String msg = node.path("msg").asText(null);
            return (msg == null || msg.isBlank()) ? "提现申请失败，请重试" : msg;
        } catch (Exception error) {
            log.warn("apply withdraw failed for APP user {}", appUserId, error);
            return "提现服务暂不可用，请稍后再试";
        }
    }

    /** Withdrawal history from the 二开后台, mapped to the APP shape (snake_case). */
    public List<Map<String, Object>> listWithdrawals(long appUserId) {
        if (!enabled || appUserId <= 0) return List.of();
        try {
            Long uid = nodeUserId(appUserId);
            if (uid == null) return List.of();
            return nodeJdbc.query("""
                    SELECT id, amount, type, account, real_name, qr_code, status, remark, reject_reason, create_time, audit_time, process_time
                      FROM withdraw WHERE user_id=? ORDER BY create_time DESC
                    """, (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                int type = rs.getInt("type");
                int status = rs.getInt("status");
                m.put("id", rs.getLong("id"));
                m.put("amount", rs.getBigDecimal("amount"));
                m.put("method", switch (type) { case 1 -> "wechat"; case 2 -> "alipay"; case 3 -> "bank_card"; default -> String.valueOf(type); });
                m.put("account_name", rs.getString("real_name"));
                m.put("account_no", type == 3 ? rs.getString("account") : null);
                m.put("qr_code_url", rs.getString("qr_code"));
                m.put("status", switch (status) { case 0 -> "pending"; case 1 -> "approved"; case 2 -> "rejected"; case 3 -> "paid"; case 4 -> "failed"; default -> "pending"; });
                String note = rs.getString("remark");
                if (note == null || note.isBlank()) note = rs.getString("reject_reason");
                m.put("review_note", note);
                m.put("created_at", ts(rs, "create_time"));
                java.sql.Timestamp reviewed = rs.getTimestamp("audit_time") != null ? rs.getTimestamp("audit_time") : rs.getTimestamp("process_time");
                m.put("reviewed_at", reviewed == null ? null : reviewed.toLocalDateTime().toString());
                return m;
            }, uid);
        } catch (RuntimeException error) {
            log.warn("list withdrawals failed for APP user {}", appUserId, error);
            return List.of();
        }
    }

    /** 钱包明细(以二开后台为准: 设备收益 + 提现), 已驳回的提现不计入(金额已退回). */
    public List<Map<String, Object>> ledger(long appUserId) {
        if (!enabled || appUserId <= 0) return List.of();
        try {
            Long uid = nodeUserId(appUserId);
            if (uid == null) return List.of();
            List<LedgerRow> rows = new ArrayList<>();
            nodeJdbc.query("SELECT id, amount, date FROM device_earnings WHERE user_id=? ORDER BY date ASC, id ASC", rs -> {
                java.sql.Date d = rs.getDate("date");
                java.sql.Timestamp at = d == null ? new java.sql.Timestamp(0L)
                        : java.sql.Timestamp.valueOf(d.toLocalDate().atStartOfDay());
                rows.add(new LedgerRow(at, rs.getLong("id"), rs.getBigDecimal("amount"), "credit", "earnings",
                        "设备收益结算 " + (d == null ? "" : d.toLocalDate())));
            }, uid);
            nodeJdbc.query("SELECT id, amount, status, create_time FROM withdraw WHERE user_id=? ORDER BY create_time ASC", rs -> {
                int status = rs.getInt("status");
                if (status == 2) return; // 已驳回: 金额已退回, 净额为零, 不在明细中体现
                java.sql.Timestamp at = rs.getTimestamp("create_time");
                String desc = status == 3 ? "提现打款" : "提现申请";
                rows.add(new LedgerRow(at, rs.getLong("id") + 100000000L, rs.getBigDecimal("amount"), "debit", "withdraw", desc));
            }, uid);
            rows.sort(Comparator.comparing(LedgerRow::at));
            List<Map<String, Object>> out = new ArrayList<>();
            BigDecimal running = BigDecimal.ZERO;
            for (LedgerRow r : rows) {
                running = "credit".equals(r.direction()) ? running.add(r.amount()) : running.subtract(r.amount());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.id());
                m.put("amount", r.amount());
                m.put("balance_after", running.setScale(2, RoundingMode.HALF_UP));
                m.put("direction", r.direction());
                m.put("entry_type", r.entryType());
                m.put("description", r.description());
                m.put("created_at", r.at().toLocalDateTime().toString());
                out.add(m);
            }
            Collections.reverse(out); // 最新在前
            return out;
        } catch (RuntimeException error) {
            log.warn("ledger query failed for APP user {}", appUserId, error);
            return List.of();
        }
    }

    private record LedgerRow(java.sql.Timestamp at, long id, BigDecimal amount, String direction, String entryType, String description) {}

    private static String ts(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        java.sql.Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime().toString();
    }

    /** Daily earnings grouped by date from the 二开 backend, newest first. */
    public List<DailyEarning> dailyEarnings(long appUserId) {
        if (!enabled || appUserId <= 0) return List.of();
        try {
            String phone = appJdbc.query("SELECT phone FROM app_user_account WHERE id = ? LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, appUserId);
            if (phone == null || phone.isBlank()) return List.of();

            // Fetch raw rows then aggregate by date in memory.
            record Row(BigDecimal amount, LocalDate date) {}
            List<Row> rows = nodeJdbc.query("""
                    SELECT de.amount, de.date AS earning_date
                      FROM device_earnings de
                      JOIN app_user u ON u.id = de.user_id
                     WHERE u.phone = ? AND (u.deleted = 0 OR u.deleted IS NULL)
                     ORDER BY de.date DESC, de.id ASC
                    """, (rs, i) -> new Row(
                    rs.getBigDecimal("amount") == null ? BigDecimal.ZERO : rs.getBigDecimal("amount"),
                    rs.getDate("earning_date") == null ? null : rs.getDate("earning_date").toLocalDate()),
                    phone);

            // Merge by date (newest first).
            Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
            for (Row r : rows) {
                LocalDate d = r.date != null ? r.date : LocalDate.ofEpochDay(0);
                byDate.merge(d, r.amount, BigDecimal::add);
            }
            List<DailyEarning> result = new ArrayList<>();
            for (var e : byDate.entrySet()) {
                result.add(new DailyEarning(e.getKey().toString(), e.getValue().setScale(2, RoundingMode.HALF_UP)));
            }
            return result;
        } catch (RuntimeException error) {
            log.warn("daily earnings query failed for APP user {}", appUserId, error);
            return List.of();
        }
    }

    public record DailyEarning(String date, BigDecimal amount) {}
}
