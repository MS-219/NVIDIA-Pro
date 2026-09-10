package com.juxin.orin.app.admin;

import com.juxin.orin.app.auth.BearerTokenFilter;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import com.juxin.orin.app.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wallet/收款方式/设备收益 surface for the 二开后台 (node-admin).
 *
 * These routes run under /api/admin/app/ which is exempted from BearerTokenFilter;
 * every handler re-validates the caller with the node-admin JWT secret so the
 * independent APP data set stays protected.
 */
@RestController
@RequestMapping("/api/admin/app")
public class AppAdminWalletController {
    private final JdbcTemplate jdbc;
    private final AppProperties properties;

    public AppAdminWalletController(JdbcTemplate jdbc, AppProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @GetMapping("/payment-applies")
    public ApiResponse<List<Map<String, Object>>> paymentApplies(@RequestParam(defaultValue = "") String status,
                                                                  HttpServletRequest request) {
        requireAdmin(request);
        String st = status == null ? "" : status.trim();
        return ApiResponse.success(jdbc.query(
                "SELECT p.*, u.phone, u.nickname FROM app_payment_apply p JOIN app_user_account u ON u.id=p.user_id WHERE (?='' OR p.status=?) ORDER BY p.created_at DESC LIMIT 500",
                (rs, row) -> row(rs, "id", "user_id", "phone", "nickname", "method", "account_name", "account_no", "qr_code_url", "status", "review_note", "reviewed_by", "reviewed_at", "created_at"),
                st, st));
    }

    @PostMapping("/payment-applies/{id}/{action}")
    @Transactional
    public ApiResponse<Void> reviewPayment(@PathVariable long id, @PathVariable String action,
                                           @RequestBody(required = false) Review body, HttpServletRequest request) {
        String admin = requireAdmin(request);
        String next = switch (action) { case "approve" -> "approved"; case "reject" -> "rejected"; default -> throw new ApiException(400, "审核动作不正确"); };
        int changed = jdbc.update("UPDATE app_payment_apply SET status=?, review_note=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='pending'", next, body == null ? null : body.note(), admin, id);
        if (changed == 0) throw new ApiException(404, "申请不存在或已审核");
        audit(admin, "review_payment", "payment_apply", Long.toString(id), next);
        return ApiResponse.success();
    }

    @GetMapping("/withdrawals")
    public ApiResponse<List<Map<String, Object>>> withdrawals(@RequestParam(defaultValue = "") String status,
                                                               HttpServletRequest request) {
        requireAdmin(request);
        String st = status == null ? "" : status.trim();
        return ApiResponse.success(jdbc.query(
                "SELECT w.*, u.phone, u.nickname FROM app_withdrawal w JOIN app_user_account u ON u.id=w.user_id WHERE (?='' OR w.status=?) ORDER BY w.created_at DESC LIMIT 500",
                (rs, row) -> row(rs, "id", "user_id", "phone", "nickname", "amount", "method", "account_name", "account_no", "qr_code_url", "status", "review_note", "reviewed_by", "reviewed_at", "created_at"),
                st, st));
    }

    @PostMapping("/withdrawals/{id}/{action}")
    @Transactional
    public ApiResponse<Void> reviewWithdrawal(@PathVariable long id, @PathVariable String action,
                                              @RequestBody(required = false) Review body, HttpServletRequest request) {
        String admin = requireAdmin(request);
        String next = switch (action) { case "approve" -> "approved"; case "reject" -> "rejected"; default -> throw new ApiException(400, "审核动作不正确"); };
        int changed = jdbc.update("UPDATE app_withdrawal SET status=?, review_note=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='pending'", next, body == null ? null : body.note(), admin, id);
        if (changed == 0) throw new ApiException(404, "申请不存在或已审核");
        audit(admin, "review_withdrawal", "withdrawal", Long.toString(id), next);
        return ApiResponse.success();
    }

    @GetMapping("/device-earnings")
    public ApiResponse<List<Map<String, Object>>> deviceEarnings(HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.success(jdbc.query("""
                SELECT n.id, n.binding_code, n.owner_user_id, n.name, n.status, n.device_type,
                       n.hashrate, n.daily_earnings, n.total_earnings, n.last_reported_at, n.bound_at, n.created_at,
                       u.phone, u.nickname
                  FROM app_node n LEFT JOIN app_user_account u ON u.id = n.owner_user_id
                 ORDER BY n.total_earnings DESC, n.id DESC LIMIT 500
                """, (rs, row) -> row(rs, "id", "binding_code", "owner_user_id", "name", "status", "device_type", "hashrate", "daily_earnings", "total_earnings", "last_reported_at", "bound_at", "created_at", "phone", "nickname")));
    }

    private void audit(String admin, String action, String type, String id, String detail) {
        jdbc.update("INSERT INTO app_admin_audit_log(admin_username,action,resource_type,resource_id,detail) VALUES(?,?,?,?,?)", admin, action, type, id, detail);
    }

    private static Map<String, Object> row(java.sql.ResultSet rs, String... cols) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String c : cols) {
            Object v = rs.getObject(c);
            if (v instanceof Clob clob) v = clob.getSubString(1, (int) Math.min(clob.length(), 32768));
            m.put(c, v);
        }
        return m;
    }

    /** Accepts either an app-backend admin token or a node-admin token (二开后台). */
    private String requireAdmin(HttpServletRequest request) {
        if ("app-admin".equals(request.getAttribute(BearerTokenFilter.USER_TYPE_ATTRIBUTE))) {
            Object v = request.getAttribute("juxin.app.adminUsername");
            return v == null ? "admin" : v.toString();
        }
        String token = request.getHeader("Authorization");
        String secret = properties.getNodeAdminJwtSecret();
        try {
            if (token == null || secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException();
            var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).build()
                    .parseSignedClaims(token.replaceFirst("^Bearer\\s+", "")).getPayload();
            if (!"admin".equals(claims.get("userType", String.class))) throw new IllegalArgumentException();
            Object username = claims.get("username");
            return username == null ? "admin" : username.toString();
        } catch (Exception error) {
            throw new ApiException(403, "需要管理员权限");
        }
    }

    public record Review(@Size(max = 255) String note) {}
}
