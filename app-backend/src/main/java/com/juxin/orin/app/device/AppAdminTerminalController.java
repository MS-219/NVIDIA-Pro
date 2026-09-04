package com.juxin.orin.app.device;

import com.juxin.orin.app.auth.BearerTokenFilter;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/edge/terminal")
public class AppAdminTerminalController {
    private final JdbcTemplate jdbc;
    private final AppTerminalTicketService tickets;

    public AppAdminTerminalController(JdbcTemplate jdbc, AppTerminalTicketService tickets) {
        this.jdbc = jdbc;
        this.tickets = tickets;
    }

    @PostMapping("/ticket/{sn}")
    public ApiResponse<Map<String, String>> ticket(@PathVariable String sn, HttpServletRequest request) {
        if (!"app-admin".equals(request.getAttribute(BearerTokenFilter.USER_TYPE_ATTRIBUTE))) {
            throw new ApiException(403, "需要管理员权限");
        }
        String normalized = sn == null ? "" : sn.trim().toUpperCase();
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_edge_device WHERE device_sn = ?", Integer.class, normalized);
        if (count == null || count == 0) throw new ApiException(404, "设备不存在");
        return ApiResponse.success(Map.of("ticket", tickets.issue(normalized), "deviceSn", normalized));
    }
}
