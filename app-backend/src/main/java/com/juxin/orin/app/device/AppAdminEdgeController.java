package com.juxin.orin.app.device;

import com.juxin.orin.app.auth.BearerTokenFilter;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/edge")
public class AppAdminEdgeController {
    private final JdbcTemplate jdbc;

    public AppAdminEdgeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> devices(HttpServletRequest request) {
        requireAdmin(request);
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT d.device_sn, d.binding_code, d.agent_version, d.image_version,
                       d.telemetry_json, d.last_reported_at, d.updated_at,
                       n.id AS node_id, n.name, n.status, n.temperature, n.hashrate,
                       n.owner_user_id
                  FROM app_edge_device d
                  LEFT JOIN app_node n ON n.binding_code = d.binding_code
                 ORDER BY d.updated_at DESC
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sn", rs.getString("device_sn"));
            row.put("bindingCode", rs.getString("binding_code"));
            row.put("agentVersion", rs.getString("agent_version"));
            row.put("imageVersion", rs.getString("image_version"));
            row.put("telemetry", rs.getString("telemetry_json"));
            row.put("lastReportedAt", rs.getTimestamp("last_reported_at"));
            row.put("updatedAt", rs.getTimestamp("updated_at"));
            row.put("nodeId", rs.getObject("node_id"));
            row.put("name", rs.getString("name"));
            row.put("status", rs.getString("status"));
            row.put("temperature", rs.getBigDecimal("temperature"));
            row.put("hashrate", rs.getBigDecimal("hashrate"));
            row.put("ownerUserId", rs.getObject("owner_user_id"));
            return row;
        });
        return ApiResponse.success(rows);
    }

    @PostMapping("/devices/{sn}/commands")
    public ApiResponse<Map<String, Object>> command(@PathVariable String sn,
                                                     @Valid @RequestBody CommandRequest request,
                                                     HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        String normalizedSn = sn == null ? "" : sn.trim().toUpperCase();
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM app_edge_device WHERE device_sn = ?", Integer.class, normalizedSn);
        if (exists == null || exists == 0) throw new ApiException(404, "设备不存在");
        String type = request.commandType().trim().toUpperCase();
        String text = switch (type) {
            case "RESTART_AGENT" -> "/etc/init.d/S99juxin-rk3588 stop; /etc/init.d/S99juxin-rk3588 start";
            case "REBOOT_DEVICE" -> "sync; reboot";
            case "HEALTH_CHECK" -> "uname -a; ip -br addr; df -h /; /usr/bin/python3 --version";
            default -> throw new ApiException(400, "不支持的设备命令");
        };
        String commandNo = "RK" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        jdbc.update("""
                INSERT INTO app_edge_command (command_no, device_sn, command_type, command_text, status)
                VALUES (?, ?, ?, ?, 'pending')
                """, commandNo, normalizedSn, type, text);
        return ApiResponse.success(Map.of("commandNo", commandNo, "deviceSn", normalizedSn, "commandType", type, "status", "pending"));
    }

    @GetMapping("/commands")
    public ApiResponse<List<Map<String, Object>>> commands(HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.success(jdbc.query("""
                SELECT command_no, device_sn, command_type, status, exit_code, result_text,
                       created_at, delivered_at, completed_at
                  FROM app_edge_command ORDER BY created_at DESC LIMIT 100
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("commandNo", rs.getString("command_no"));
            row.put("deviceSn", rs.getString("device_sn"));
            row.put("commandType", rs.getString("command_type"));
            row.put("status", rs.getString("status"));
            row.put("exitCode", rs.getObject("exit_code"));
            row.put("resultText", rs.getString("result_text"));
            row.put("createdAt", rs.getTimestamp("created_at"));
            row.put("deliveredAt", rs.getTimestamp("delivered_at"));
            row.put("completedAt", rs.getTimestamp("completed_at"));
            return row;
        }));
    }

    private static void requireAdmin(HttpServletRequest request) {
        if (!"app-admin".equals(request.getAttribute(BearerTokenFilter.USER_TYPE_ATTRIBUTE))) {
            throw new ApiException(403, "需要管理员权限");
        }
    }

    public record CommandRequest(@NotBlank @Size(max = 32) String commandType) {}
}
