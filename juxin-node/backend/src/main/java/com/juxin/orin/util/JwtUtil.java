package com.juxin.orin.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token 工具类 (兼容 jjwt 0.12.x)
 */
public class JwtUtil {

    private static final String SECRET_KEY = resolveSecretKey();

    // Token 有效期：小程序用户 30 天，管理员 7 天
    private static final long APP_EXPIRATION_TIME = 30L * 24 * 60 * 60 * 1000;
    private static final long ADMIN_EXPIRATION_TIME = 7L * 24 * 60 * 60 * 1000;

    // 生成密钥
    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    private static String resolveSecretKey() {
        String configured = System.getenv("ORIN_JWT_SECRET");
        if (configured != null && configured.getBytes(StandardCharsets.UTF_8).length >= 32) {
            return configured;
        }

        byte[] generated = new byte[48];
        new SecureRandom().nextBytes(generated);
        return Base64.getEncoder().encodeToString(generated);
    }

    /**
     * 生成 Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param userType 用户类型: "admin" | "app"
     */
    public static String generateToken(Long userId, String username, String userType) {
        return generateToken(userId, username, userType, null);
    }

    /**
     * 生成 Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param userType 用户类型: "admin" | "app"
     * @param role     后台角色：admin / factory
     */
    public static String generateToken(Long userId, String username, String userType, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("userType", userType);
        if (role != null && !role.isBlank()) {
            claims.put("role", role);
        }

        // 管理员 7 天，小程序用户 30 天
        long expiration = "admin".equals(userType) ? ADMIN_EXPIRATION_TIME : APP_EXPIRATION_TIME;

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Token 获取用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object userId = claims.get("userId");
            if (userId instanceof Number) {
                return ((Number) userId).longValue();
            }
            if (userId instanceof String) {
                try {
                    return Long.valueOf((String) userId);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 从 Token 获取用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object username = claims.get("username");
        return username == null ? claims.getSubject() : String.valueOf(username);
    }

    /**
     * 从 Token 获取用户类型
     */
    public static String getUserType(String token) {
        Claims claims = parseToken(token);
        return claims != null ? (String) claims.get("userType") : null;
    }

    /**
     * 从 Token 获取后台角色
     */
    public static String getRole(String token) {
        Claims claims = parseToken(token);
        return claims != null ? (String) claims.get("role") : null;
    }

    /**
     * 验证 Token 是否有效
     */
    public static boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        return claims.getExpiration().after(new Date());
    }
}
