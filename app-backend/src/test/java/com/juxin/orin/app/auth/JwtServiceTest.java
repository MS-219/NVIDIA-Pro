package com.juxin.orin.app.auth;

import com.juxin.orin.app.config.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtServiceTest {

    @Test
    void tokenContainsOnlyNonSensitiveIdentityClaims() {
        AppProperties properties = new AppProperties();
        properties.setJwtSecret("01234567890123456789012345678901");
        JwtService service = new JwtService(properties);

        String token = service.issue(new UserAccount(42L, "13800138000", "Test", Instant.now()));
        Claims claims = service.parse(token);

        assertEquals(42L, ((Number) claims.get("userId")).longValue());
        assertEquals("app", claims.get("userType"));
        assertNull(claims.get("phone"));
    }
}
