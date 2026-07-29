package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.SysUser;
import com.juxin.orin.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理员登录控制器
 */
@RestController
@RequestMapping("/api/admin")
public class SysUserController {

    private static final Logger log = LoggerFactory.getLogger(SysUserController.class);
    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public Result<Object> login(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String username = params.get("username");
        String password = params.get("password");
        String loginIp = getClientIp(request);

        if (username == null || password == null) {
            log.warn("管理后台登录失败: username={}, ip={}, reason=用户名或密码为空", safeUsername(username), loginIp);
            return Result.error("用户名和密码不能为空");
        }

        String token = sysUserService.login(username, password);
        if (token == null) {
            log.warn("管理后台登录失败: username={}, ip={}, reason=用户名或密码错误", safeUsername(username), loginIp);
            return Result.error("用户名或密码错误");
        }

        // 获取用户信息以返回角色
        SysUser user = sysUserService.lambdaQuery().eq(SysUser::getUsername, username).one();
        String role = (user != null && user.getRole() != null) ? user.getRole() : "admin";

        log.info("管理后台登录成功: username={}, userId={}, role={}, ip={}",
                safeUsername(username),
                user != null ? user.getId() : null,
                role,
                loginIp);

        // 返回 Token、账号和角色
        return Result.success(Map.of(
                "token", token,
                "role", role,
                "username", user != null ? user.getUsername() : username));
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

    private String safeUsername(String username) {
        return username == null || username.isEmpty() ? "-" : username;
    }

    /**
     * 获取当前登录用户信息（需要 Token）
     */
    @GetMapping("/info")
    public Result<Object> info(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || token.isEmpty()) {
            return Result.error("未登录");
        }

        // 去除 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Long userId = com.juxin.orin.util.JwtUtil.getUserId(token);
        if (userId == null) {
            return Result.error("Token 无效");
        }

        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 不返回密码
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 修改密码
     */
    @PostMapping("/changePassword")
    public Result<Object> changePassword(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String, String> params) {

        if (token == null || token.isEmpty()) {
            return Result.error("未登录");
        }

        // 去除 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Long userId = com.juxin.orin.util.JwtUtil.getUserId(token);
        if (userId == null) {
            return Result.error("Token 无效");
        }

        String currentPassword = params.get("currentPassword");
        String newPassword = params.get("newPassword");

        if (currentPassword == null || currentPassword.isEmpty()) {
            return Result.error("当前密码不能为空");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            return Result.error("新密码不能为空");
        }
        if (newPassword.length() < 12) {
            return Result.error("新密码长度不能少于12位");
        }

        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 验证当前密码
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return Result.error("当前密码错误");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        boolean success = sysUserService.updateById(user);

        if (success) {
            return Result.success("密码修改成功");
        } else {
            return Result.error("密码修改失败");
        }
    }
}
