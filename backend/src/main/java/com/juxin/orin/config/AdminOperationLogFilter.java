package com.juxin.orin.config;

import com.juxin.orin.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class AdminOperationLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminOperationLogFilter.class);
    private static final Set<String> FACTORY_ALLOWED_EXACT_PATHS = new HashSet<>(Arrays.asList(
            "/api/admin/info",
            "/api/device/export-sn"));
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
            "/api/admin/login"));
    private static final Set<String> ADMIN_PREFIXES = new HashSet<>(Arrays.asList(
            "/api/admin",
            "/api/notice/admin",
            "/api/invite/admin",
            "/api/withdraw/admin",
            "/api/earnings/admin"));
    private static final Set<String> ADMIN_EXACT_PATHS = new HashSet<>(Arrays.asList(
            "/api/statistics/dashboard",
            "/api/statistics/trend",
            "/api/feedback/list",
            "/api/feedback/process",
            "/api/device/all",
            "/api/device/stats",
            "/api/device/export-sn",
            "/api/device/push-command",
            "/api/device/batch-unbind",
            "/api/device/release-merchant",
            "/api/device/batch-release-merchant",
            "/api/device/batch-delete",
            "/api/device/admin-bind",
            "/api/device/create",
            "/api/device/batch-create",
            "/api/user/list",
            "/api/user/stats",
            "/api/user/update",
            "/api/user/updateLevel",
            "/api/user/toggleWithdraw",
            "/api/user/transfer-contract",
            "/api/user/updateInviter",
            "/api/user/recharge-quota",
            "/api/user/refresh-levels",
            "/api/settings/all",
            "/api/settings/earnings",
            "/api/settings/ai-pricing",
            "/api/settings/device",
            "/api/settings/system",
            "/api/settings/withdraw-days",
            "/api/earnings/stats"));
    private static final Set<String> ADMIN_PATH_CONTAINS = new HashSet<>(Arrays.asList(
            "/api/device/delete/",
            "/api/feedback/delete/",
            "/api/user/detail/",
            "/api/user/syncContract/"));

    private final AdminAuthValidator adminAuthValidator;

    public AdminOperationLogFilter(AdminAuthValidator adminAuthValidator) {
        this.adminAuthValidator = adminAuthValidator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || EXCLUDED_PATHS.contains(uri) || (!isAdminManagedPath(uri) && !hasAdminToken(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authError = validateAdminRequest(request);
        if (authError != null) {
            writeAuthError(response, authError);
            log.warn("管理后台鉴权失败: ip={}, method={}, path={}, reason={}",
                    getClientIp(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    authError);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            String path = request.getRequestURI();
            if (!EXCLUDED_PATHS.contains(path)) {
                long duration = System.currentTimeMillis() - startTime;
                String ip = getClientIp(request);
                String method = request.getMethod();
                String query = request.getQueryString();
                String body = getRequestBody(wrappedRequest);
                String operator = getOperator(request);
                Long operatorId = getOperatorId(request);
                int status = response.getStatus();

                if (query != null && !query.isEmpty()) {
                    path = path + "?" + query;
                }

                log.info("管理后台操作: operator={}, userId={}, ip={}, method={}, path={}, status={}, durationMs={}, body={}",
                        operator,
                        operatorId,
                        ip,
                        method,
                        path,
                        status,
                        duration,
                        body);
            }
        }
    }

    private String getOperator(HttpServletRequest request) {
        Claims claims = parseAdminClaims(request);
        if (claims == null) {
            return "-";
        }
        Object username = claims.get("username");
        return username == null ? claims.getSubject() : String.valueOf(username);
    }

    private Long getOperatorId(HttpServletRequest request) {
        Claims claims = parseAdminClaims(request);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return userId instanceof Long ? (Long) userId : null;
    }

    private Claims parseAdminClaims(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (adminAuthValidator.validate(token) != null) {
            return null;
        }
        return JwtUtil.parseToken(adminAuthValidator.normalizeToken(token));
    }

    private String validateAdminRequest(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (EXCLUDED_PATHS.contains(path)) {
            return null;
        }

        String authError = adminAuthValidator.validate(request.getHeader("Authorization"));
        if (authError != null) {
            return authError;
        }

        if (isFactoryToken(request) && !FACTORY_ALLOWED_EXACT_PATHS.contains(path)) {
            return "工厂账号仅可导出设备二维码";
        }

        return null;
    }

    private boolean isAdminManagedPath(String uri) {
        if (uri == null) {
            return false;
        }
        for (String prefix : ADMIN_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        if (ADMIN_EXACT_PATHS.contains(uri)) {
            return true;
        }
        for (String contains : ADMIN_PATH_CONTAINS) {
            if (uri.startsWith(contains)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAdminToken(HttpServletRequest request) {
        String token = adminAuthValidator.normalizeToken(request.getHeader("Authorization"));
        return token != null && "admin".equals(JwtUtil.getUserType(token));
    }

    private boolean isFactoryToken(HttpServletRequest request) {
        String token = adminAuthValidator.normalizeToken(request.getHeader("Authorization"));
        return token != null
                && "admin".equals(JwtUtil.getUserType(token))
                && "factory".equals(JwtUtil.getRole(token));
    }

    private void writeAuthError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        String safeMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + safeMessage + "\",\"data\":null}");
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return "-";
        }
        if (!contentType.contains(MediaType.APPLICATION_JSON_VALUE)
                && !contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            return "-";
        }

        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return "-";
        }

        String body = new String(content, StandardCharsets.UTF_8)
                .replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"")
                .replaceAll("(?i)\"currentPassword\"\\s*:\\s*\"[^\"]*\"", "\"currentPassword\":\"***\"")
                .replaceAll("(?i)\"newPassword\"\\s*:\\s*\"[^\"]*\"", "\"newPassword\":\"***\"");

        if (body.length() > 1000) {
            return body.substring(0, 1000) + "...";
        }
        return body;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
