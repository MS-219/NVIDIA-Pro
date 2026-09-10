package com.juxin.orin.app.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Validates Bearer tokens for protected endpoints while leaving login routes public. */
@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    /** Request-scoped identity populated only after a JWT has been verified. */
    public static final String USER_ID_ATTRIBUTE = BearerTokenFilter.class.getName() + ".userId";
    public static final String USER_TYPE_ATTRIBUTE = BearerTokenFilter.class.getName() + ".userType";
    private final JwtService jwtService;

    public BearerTokenFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.equals("/api/admin/login")
                || path.equals("/api/auth/sms/send")
                || path.equals("/api/auth/sms/login")
                || path.equals("/api/health")
                || path.equals("/api/mobile-app/update")
                || path.startsWith("/api/app/update/")
                // The 二开后台 sends its own admin JWT to the shared update routes.
                // AppUpdateController validates that token with the node-admin secret.
                || path.startsWith("/api/admin/app-updates")
                // 二开后台通过自己的 node-admin JWT 访问 APP 钱包/收款方式/设备收益，
                // 由 AppAdminWalletController 用 node-admin 密钥做二次校验。
                || path.startsWith("/api/admin/app/")
                // 收款码图片对后台浏览器公开（<img> 不携带 Authorization）。
                || path.startsWith("/api/app/files/")
                || path.startsWith("/api/edge/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        try {
            var claims = jwtService.requireClaims(authorization);
            Object userId = claims.get("userId");
            if (userId instanceof Number number) {
                request.setAttribute(USER_ID_ATTRIBUTE, number.longValue());
            }
            request.setAttribute(USER_TYPE_ATTRIBUTE, claims.get("userType", String.class));
            request.setAttribute("juxin.app.adminUsername", claims.get("username", String.class));
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\",\"data\":null}");
        }
    }
}
