package com.juxin.orin.config;

import com.juxin.orin.entity.SysUser;
import com.juxin.orin.service.ISysUserService;
import com.juxin.orin.util.JwtUtil;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthValidator {

    private final ISysUserService sysUserService;

    public AdminAuthValidator(ISysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    public String validate(String authorization) {
        String token = normalizeToken(authorization);
        if (token == null || token.isEmpty()) {
            return "未登录，请先登录";
        }
        if (!JwtUtil.validateToken(token)) {
            return "登录已过期，请重新登录";
        }
        if (!"admin".equals(JwtUtil.getUserType(token))) {
            return "无权限访问此接口";
        }

        Long userId = JwtUtil.getUserId(token);
        String tokenUsername = JwtUtil.getUsername(token);
        if (userId == null || tokenUsername == null || tokenUsername.isBlank()) {
            return "登录状态异常，请重新登录";
        }

        SysUser currentUser = sysUserService.getById(userId);
        if (currentUser == null) {
            return "账号不存在，请重新登录";
        }
        if (!tokenUsername.equals(currentUser.getUsername())) {
            return "账号信息已变更，请重新登录";
        }
        String currentRole = currentUser.getRole() == null || currentUser.getRole().isBlank()
                ? "admin"
                : currentUser.getRole();
        if (!"admin".equals(currentRole) && !"factory".equals(currentRole)) {
            return "账号权限已变更，请重新登录";
        }
        String tokenRole = JwtUtil.getRole(token);
        if (tokenRole == null || tokenRole.isBlank() || !tokenRole.equals(currentRole)) {
            return "账号权限已变更，请重新登录";
        }

        return null;
    }

    public Long getAdminId(String authorization) {
        String token = normalizeToken(authorization);
        if (validate(authorization) != null) {
            return null;
        }
        return JwtUtil.getUserId(token);
    }

    public String normalizeToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String token = authorization.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        return token;
    }
}
