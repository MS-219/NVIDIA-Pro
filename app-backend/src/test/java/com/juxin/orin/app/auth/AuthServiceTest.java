package com.juxin.orin.app.auth;

import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private FakeChallengeStore challenges;
    private FakeUsers users;
    private FakeSmsGateway sms;
    private AuthService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setJwtSecret("01234567890123456789012345678901");
        properties.getSms().setPepper("test-pepper-that-is-not-a-production-secret");
        properties.getSms().setCooldownSeconds(60);
        properties.getSms().setCodeTtlSeconds(300);
        challenges = new FakeChallengeStore();
        users = new FakeUsers();
        sms = new FakeSmsGateway();
        service = new AuthService(challenges, users, sms, new JwtService(properties), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sendsCodeAndConsumesItOnce() {
        AuthService.SendCodeResult sent = service.sendLoginCode("13800138000", "127.0.0.1");
        assertNotNull(sent.providerRequestId());
        assertEquals("13800138000", sms.phone);

        AuthService.LoginResult result = service.login("13800138000", sms.code, null);
        assertEquals(100001L, result.user().id());
        assertNotNull(result.token());
        assertThrows(ApiException.class, () -> service.login("13800138000", sms.code, null));
    }

    @Test
    void rejectsWrongCodeAndAppliesCooldown() {
        service.sendLoginCode("13800138000", "127.0.0.1");
        assertThrows(ApiException.class, () -> service.login("13800138000", "000000", null));
        assertThrows(ApiException.class, () -> service.sendLoginCode("13800138000", "127.0.0.1"));
        assertEquals(1, challenges.attempts);
    }

    private static final class FakeSmsGateway implements SmsGateway {
        String phone;
        String code;

        @Override
        public SendResult sendLoginCode(String phone, String code, String requestId) {
            this.phone = phone;
            this.code = code;
            return new SendResult("provider-" + requestId);
        }
    }

    private static final class FakeChallengeStore implements SmsChallengeStore {
        private final List<Challenge> rows = new ArrayList<>();
        private int attempts;

        @Override
        public boolean sentRecently(String phone, String clientIp, Instant since) {
            return rows.stream().anyMatch(row -> row.phone().equals(phone));
        }

        @Override
        public void save(String phone, String codeHash, Instant expiresAt, String clientIp, String requestId,
                         String providerRequestId, Instant createdAt) {
            rows.add(new Challenge(rows.size() + 1, phone, codeHash, expiresAt, 0));
        }

        @Override
        public Optional<Challenge> findLatestActive(String phone, Instant now) {
            return rows.stream().filter(row -> row.phone().equals(phone)).reduce((a, b) -> b);
        }

        @Override
        public boolean consume(long id, Instant consumedAt) {
            rows.removeIf(row -> row.id() == id);
            return true;
        }

        @Override
        public void incrementAttempts(long id) {
            attempts++;
        }
    }

    private static final class FakeUsers implements UserAccountRepository {
        private UserAccount user;

        @Override
        public Optional<UserAccount> findByPhone(String phone) {
            return Optional.ofNullable(user);
        }

        @Override
        public Optional<UserAccount> findById(long id) {
            return Optional.ofNullable(user).filter(value -> value.id() == id);
        }

        @Override
        public UserAccount create(String phone, String nickname) {
            user = new UserAccount(100001L, phone, nickname, NOW);
            return user;
        }

        @Override
        public Optional<UserAccount> updateNickname(long id, String nickname) {
            if (user == null || user.id() != id) return Optional.empty();
            user = new UserAccount(user.id(), user.phone(), nickname, user.createdAt());
            return Optional.of(user);
        }
    }
}
