package com.juxin.orin.app.auth;

import com.juxin.orin.app.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private static final Duration TOKEN_TTL = Duration.ofDays(30);
    private final AppProperties properties;
    private SecretKey signingKey;

    public JwtService(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt-secret must contain at least 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UserAccount user) {
        ensureInitialized();
        Date now = new Date();
        return Jwts.builder()
                .claims(Map.of(
                        "userId", user.id(),
                        "userType", "app"))
                .subject(Long.toString(user.id()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOKEN_TTL.toMillis()))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        ensureInitialized();
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public long requireUserId(String token) {
        Claims claims = parse(stripBearer(token));
        if (!"app".equals(claims.get("userType", String.class))) {
            throw new IllegalArgumentException("token userType is not app");
        }
        Object value = claims.get("userId");
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("token missing userId");
        }
        return number.longValue();
    }

    public static String stripBearer(String token) {
        if (token == null) {
            return "";
        }
        return token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
    }

    private void ensureInitialized() {
        if (signingKey == null) {
            initialize();
        }
    }
}
