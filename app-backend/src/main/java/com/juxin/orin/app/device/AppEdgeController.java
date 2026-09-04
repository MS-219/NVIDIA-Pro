package com.juxin.orin.app.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Device protocol for RK3588 nodes connected through jd.ldjuxin.yun. */
@RestController
@RequestMapping("/api/edge")
public class AppEdgeController {
    private static final String TOKEN_HEADER = "X-RK3588-Device-Token";
    private static final String LEGACY_TOKEN_HEADER = "X-Orin-Device-Token";
    private static final String GENERIC_TOKEN_HEADER = "X-Juxin-Device-Token";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AppEdgeController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/enroll")
    public ApiResponse<Map<String, Object>> enroll(@Valid @RequestBody EnrollRequest request) {
        String sn = normalizeSn(request.sn());
        Instant now = Instant.now();
        String token = randomToken();
        String tokenHash = sha256(token);
        String bindingCode = jdbc.query(
                "SELECT binding_code FROM app_edge_device WHERE device_sn = ? LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, sn);
        if (bindingCode == null || bindingCode.isBlank()) {
            bindingCode = newBindingCode();
            jdbc.update("""
                    INSERT INTO app_edge_device
                        (device_sn, binding_code, device_token_hash, hardware_fingerprint,
                         agent_version, image_version, telemetry_json, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, sn, bindingCode, tokenHash, clean(request.hardwareFingerprint()),
                    clean(request.agentVersion()), clean(request.imageVersion()), "{}", timestamp(now));
        } else {
            jdbc.update("""
                    UPDATE app_edge_device
                       SET device_token_hash = ?, hardware_fingerprint = ?, agent_version = ?,
                           image_version = ?, updated_at = ?
                     WHERE device_sn = ?
                    """, tokenHash, clean(request.hardwareFingerprint()), clean(request.agentVersion()),
                    clean(request.imageVersion()), timestamp(now), sn);
        }
        int nodeUpdated = jdbc.update(
                "UPDATE app_node SET updated_at = ? WHERE binding_code = ?",
                timestamp(now), bindingCode);
        if (nodeUpdated == 0) {
            jdbc.update("""
                    INSERT INTO app_node (binding_code, name, status, updated_at)
                    VALUES (?, ?, 'pending', ?)
                    """, bindingCode, "RK3588S 节点", timestamp(now));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceSn", sn);
        data.put("deviceToken", token);
        data.put("bindCode", bindingCode);
        data.put("platform", "rk3588s");
        data.put("heartbeatInterval", 60);
        data.put("taskPollInterval", 60);
        data.put("offlineThreshold", 180);
        data.put("powerMode", "RK_DEFAULT");
        return ApiResponse.success(data);
    }

    @PostMapping("/report")
    public ApiResponse<Map<String, Object>> report(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = LEGACY_TOKEN_HEADER, required = false) String legacyToken,
            @RequestHeader(value = GENERIC_TOKEN_HEADER, required = false) String genericToken,
            @RequestBody Map<String, Object> payload) {
        String verifiedToken = firstNonBlank(token, legacyToken, genericToken);
        EdgeDevice device = requireDevice(verifiedToken);
        Instant now = Instant.now();
        String telemetry = toJson(payload);
        jdbc.update("""
                UPDATE app_edge_device
                   SET agent_version = ?, image_version = ?, telemetry_json = ?,
                       last_reported_at = ?, updated_at = ?
                 WHERE id = ?
                """, clean(value(payload, "agent_version")), clean(value(payload, "image_version")),
                telemetry, timestamp(now), timestamp(now), device.id());
        BigDecimal temperature = decimal(payload.get("temperature"), payload.get("gpu_temperature"));
        BigDecimal hashrate = decimal(payload.get("hashrate"), payload.get("gpu_usage"));
        jdbc.update("""
                UPDATE app_node
                   SET status = 'online', temperature = ?, hashrate = ?, last_reported_at = ?, updated_at = ?
                 WHERE binding_code = ?
                """, temperature, hashrate, timestamp(now), timestamp(now), device.bindingCode());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("heartbeatInterval", 60);
        data.put("taskPollInterval", 60);
        data.put("offlineThreshold", 180);
        data.put("powerMode", "RK_DEFAULT");
        data.put("platform", "rk3588s");
        return ApiResponse.success(data);
    }

    @GetMapping("/tasks/fetch")
    public ApiResponse<Map<String, Object>> fetchTasks(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = LEGACY_TOKEN_HEADER, required = false) String legacyToken,
            @RequestHeader(value = GENERIC_TOKEN_HEADER, required = false) String genericToken,
            HttpServletRequest request) {
        requireDevice(firstNonBlank(token, legacyToken, genericToken));
        // Task scheduling is intentionally added after the first RK pilot is online.
        return ApiResponse.success(null);
    }

    @PostMapping("/tasks/submit")
    public ApiResponse<Void> submitTask(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = LEGACY_TOKEN_HEADER, required = false) String legacyToken,
            @RequestHeader(value = GENERIC_TOKEN_HEADER, required = false) String genericToken,
            @RequestBody Map<String, Object> payload) {
        requireDevice(firstNonBlank(token, legacyToken, genericToken));
        return ApiResponse.success();
    }

    private EdgeDevice requireDevice(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(401, "设备令牌缺失");
        }
        String hash = sha256(token.trim());
        EdgeDevice device = jdbc.query("""
                SELECT id, binding_code FROM app_edge_device WHERE device_token_hash = ? LIMIT 1
                """, (rs, rowNum) -> new EdgeDevice(rs.getLong("id"), rs.getString("binding_code")), hash)
                .stream().findFirst().orElse(null);
        if (device == null) throw new ApiException(401, "设备令牌无效");
        return device;
    }

    private String newBindingCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app_edge_device WHERE binding_code = ?", Integer.class, code);
            if (count == null || count == 0) return code;
        }
        throw new ApiException(500, "无法生成设备绑定码");
    }

    private static String normalizeSn(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9][A-Z0-9._:-]{5,63}")) {
            throw new ApiException(400, "设备编号格式不正确");
        }
        return value;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            return value.length() <= 32768 ? value : value.substring(0, 32768);
        } catch (JsonProcessingException error) {
            throw new ApiException(400, "设备上报数据格式不正确");
        }
    }

    private static BigDecimal decimal(Object... values) {
        for (Object value : values) {
            if (value == null) continue;
            try { return new BigDecimal(value.toString()); } catch (NumberFormatException ignored) { }
        }
        return BigDecimal.ZERO;
    }

    private static String value(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : value.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().substring(0, Math.min(value.trim().length(), 128));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String randomCode() {
        byte[] bytes = new byte[5];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }

    private record EdgeDevice(long id, String bindingCode) { }

    public record EnrollRequest(
            @NotBlank @Size(max = 64) String sn,
            @Size(max = 64) String agentVersion,
            @Size(max = 128) String imageVersion,
            @Size(max = 64) String hardwareFingerprint) { }
}
