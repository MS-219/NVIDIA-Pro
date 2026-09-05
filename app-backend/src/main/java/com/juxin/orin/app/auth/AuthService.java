package com.juxin.orin.app.auth;

import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.PhoneNumberNormalizer;
import com.juxin.orin.app.config.AppProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("\\d{6}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsChallengeStore challengeStore;
    private final UserAccountRepository userRepository;
    private final SmsGateway smsGateway;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final Clock clock;
    private final NodeUserSyncService nodeUserSyncService;

    @Autowired
    public AuthService(SmsChallengeStore challengeStore,
                       UserAccountRepository userRepository,
                       SmsGateway smsGateway,
                       JwtService jwtService,
                       AppProperties properties,
                       NodeUserSyncService nodeUserSyncService) {
        this(challengeStore, userRepository, smsGateway, jwtService, properties, Clock.systemUTC(), nodeUserSyncService);
    }

    AuthService(SmsChallengeStore challengeStore,
                UserAccountRepository userRepository,
                SmsGateway smsGateway,
                JwtService jwtService,
                AppProperties properties,
                Clock clock) {
        this(challengeStore, userRepository, smsGateway, jwtService, properties, clock, null);
    }

    AuthService(SmsChallengeStore challengeStore,
                UserAccountRepository userRepository,
                SmsGateway smsGateway,
                JwtService jwtService,
                AppProperties properties,
                Clock clock,
                NodeUserSyncService nodeUserSyncService) {
        this.challengeStore = challengeStore;
        this.userRepository = userRepository;
        this.smsGateway = smsGateway;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
        this.nodeUserSyncService = nodeUserSyncService;
    }

    @Transactional
    public SendCodeResult sendLoginCode(String rawPhone, String clientIp) {
        String phone = PhoneNumberNormalizer.normalize(rawPhone);
        Instant now = Instant.now(clock);
        Duration cooldown = Duration.ofSeconds(clamp(properties.getSms().getCooldownSeconds(), 1, 3600));
        if (challengeStore.sentRecently(phone, safeIp(clientIp), now.minus(cooldown))) {
            throw new ApiException(429, "验证码发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String requestId = UUID.randomUUID().toString();
        Instant expiresAt = now.plusSeconds(clamp(properties.getSms().getCodeTtlSeconds(), 60, 900));
        String hash = hashCode(phone, code);

        SmsGateway.SendResult result = smsGateway.sendLoginCode(phone, code, requestId);
        challengeStore.save(phone, hash, expiresAt, safeIp(clientIp), requestId, result.providerRequestId(), now);
        return new SendCodeResult(result.providerRequestId(), cooldown.getSeconds());
    }

    @Transactional
    public LoginResult login(String rawPhone, String rawCode, String nickname) {
        String phone = PhoneNumberNormalizer.normalize(rawPhone);
        if (rawCode == null || !CODE_PATTERN.matcher(rawCode.trim()).matches()) {
            throw new ApiException(400, "请输入 6 位验证码");
        }
        Instant now = Instant.now(clock);
        SmsChallengeStore.Challenge challenge = challengeStore.findLatestActive(phone, now)
                .orElseThrow(() -> new ApiException(400, "验证码不存在或已过期"));
        if (challenge.attempts() >= properties.getSms().getMaxAttempts()) {
            throw new ApiException(400, "验证码错误次数过多，请重新获取");
        }

        String expected = hashCode(phone, rawCode.trim());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                challenge.codeHash().getBytes(StandardCharsets.UTF_8))) {
            challengeStore.incrementAttempts(challenge.id());
            throw new ApiException(400, "验证码错误");
        }
        if (!challengeStore.consume(challenge.id(), now)) {
            throw new ApiException(400, "验证码已使用，请重新获取");
        }

        String normalizedNickname = nickname == null || nickname.isBlank() ? "Orin 用户" : nickname.trim();
        if (normalizedNickname.length() > 40) {
            normalizedNickname = normalizedNickname.substring(0, 40);
        }
        final String safeNickname = normalizedNickname;
        UserAccount user;
        try {
            user = userRepository.findByPhone(phone)
                    .orElseGet(() -> userRepository.create(phone, safeNickname));
        } catch (DataAccessException e) {
            log.error("account persistence failed for phone suffix {}", phone.substring(Math.max(0, phone.length() - 4)), e);
            throw new ApiException(503, "账号服务暂时不可用");
        }
        if (nodeUserSyncService != null) nodeUserSyncService.syncNickname(user.phone(), user.nickname());
        return new LoginResult(jwtService.issue(user), user);
    }

    public UserAccount findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "账号不存在"));
    }

    @Transactional
    public UserAccount updateNickname(long userId, String rawNickname) {
        if (userId <= 0) {
            throw new ApiException(401, "登录已过期，请重新登录");
        }
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        if (nickname.isBlank()) {
            throw new ApiException(400, "昵称不能为空");
        }
        if (nickname.length() > 40) {
            throw new ApiException(400, "昵称长度不能超过 40 个字符");
        }
        UserAccount user = userRepository.updateNickname(userId, nickname)
                .orElseThrow(() -> new ApiException(404, "账号不存在"));
        if (nodeUserSyncService != null) nodeUserSyncService.syncNickname(user.phone(), user.nickname());
        return user;
    }

    private String hashCode(String phone, String code) {
        String pepper = properties.getSms().getPepper();
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("app.sms.pepper must be configured");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((phone + ":" + code + ":" + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static String safeIp(String value) {
        return value == null || value.isBlank() ? "unknown" : value.substring(0, Math.min(value.length(), 64));
    }

    public record SendCodeResult(String providerRequestId, long retryAfterSeconds) {
    }

    public record LoginResult(String token, UserAccount user) {
    }
}
