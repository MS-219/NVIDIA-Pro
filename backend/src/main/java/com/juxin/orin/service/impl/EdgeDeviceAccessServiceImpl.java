package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.juxin.orin.dto.EdgeEnrollRequest;
import com.juxin.orin.dto.EdgeEnrollResponse;
import com.juxin.orin.entity.Device;
import com.juxin.orin.exception.EdgeDeviceApiException;
import com.juxin.orin.mapper.DeviceMapper;
import com.juxin.orin.service.IEdgeDeviceAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class EdgeDeviceAccessServiceImpl implements IEdgeDeviceAccessService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final Duration ENROLLMENT_RECOVERY_WINDOW = Duration.ofMinutes(15);
    private static final Pattern SN_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final DeviceMapper deviceMapper;
    private final byte[] enrollmentSecret;

    public EdgeDeviceAccessServiceImpl(
            DeviceMapper deviceMapper,
            @Value("${ORIN_JWT_SECRET:}") String enrollmentSecret) {
        if (enrollmentSecret == null || enrollmentSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ORIN_JWT_SECRET must contain at least 32 bytes");
        }
        this.deviceMapper = deviceMapper;
        this.enrollmentSecret = enrollmentSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public EdgeEnrollResponse enroll(EdgeEnrollRequest request, String clientIp) {
        if (request == null) {
            throw badRequest("入网参数不能为空");
        }

        String sn = requireText(request.sn(), "sn", 64);
        String imageVersion = requireText(request.imageVersion(), "image_version", 64);
        String hardwareFingerprint = requireText(
                request.hardwareFingerprint(), "hardware_fingerprint", 128);
        if (!SN_PATTERN.matcher(sn).matches()) {
            throw badRequest("sn format is invalid");
        }
        if (hardwareFingerprint.length() < 16) {
            throw badRequest("hardware_fingerprint is too short");
        }

        Device fingerprintOwner = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getHardwareFingerprint, hardwareFingerprint)
                .last("LIMIT 1"));
        if (fingerprintOwner != null && !sn.equals(fingerprintOwner.getSn())) {
            throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "硬件指纹已绑定其他设备");
        }

        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getSn, sn)
                .last("LIMIT 1"));
        boolean isNew = device == null;
        if (isNew) {
            device = new Device();
            device.setSn(sn);
            device.setBindCode(generateAvailableBindCode(sn));
            device.setCreateTime(LocalDateTime.now());
            device.setHashrate(0);
            device.setType(2);
        } else {
            validateExistingIdentity(device, hardwareFingerprint);
            if (hasText(device.getDeviceTokenHash())) {
                return recoverRecentEnrollment(
                        device, imageVersion, hardwareFingerprint, request.telemetry(), clientIp);
            }
            if (!hasText(device.getBindCode())) {
                device.setBindCode(generateAvailableBindCode(sn));
            }
        }

        String tokenSeed = randomHex(TOKEN_BYTES);
        String rawToken = deriveToken(tokenSeed, sn, hardwareFingerprint);
        LocalDateTime now = LocalDateTime.now();
        device.setDeviceTokenHash(sha256(rawToken));
        device.setDeviceTokenSeed(tokenSeed);
        device.setHardwareFingerprint(hardwareFingerprint);
        device.setEnrolledAt(now);
        device.setImageVersion(imageVersion);
        device.setStatus(1);
        device.setType(2);
        device.setLastHeartbeatTime(now);
        device.setIp(limitText(clientIp, 64));
        applyTelemetry(device, request.telemetry());

        if (isNew) {
            try {
                deviceMapper.insert(device);
            } catch (DuplicateKeyException exception) {
                return recoverConcurrentEnrollment(
                        sn, imageVersion, hardwareFingerprint, request.telemetry(), clientIp);
            }
        } else {
            int claimed = deviceMapper.update(null, new UpdateWrapper<Device>()
                    .eq("id", device.getId())
                    .isNull("device_token_hash")
                    .set("device_token_hash", device.getDeviceTokenHash())
                    .set("device_token_seed", device.getDeviceTokenSeed())
                    .set("hardware_fingerprint", hardwareFingerprint)
                    .set("enrolled_at", now)
                    .set("image_version", imageVersion));
            if (claimed != 1) {
                return recoverConcurrentEnrollment(
                        sn, imageVersion, hardwareFingerprint, request.telemetry(), clientIp);
            }
            deviceMapper.updateById(device);
        }

        return new EdgeEnrollResponse(device.getSn(), device.getId(), device.getBindCode(), rawToken);
    }

    private EdgeEnrollResponse recoverConcurrentEnrollment(
            String sn,
            String imageVersion,
            String hardwareFingerprint,
            Map<String, Object> telemetry,
            String clientIp) {
        Device enrolled = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getSn, sn)
                .last("LIMIT 1 FOR UPDATE"));
        if (enrolled == null) {
            Device fingerprintOwner = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                    .eq(Device::getHardwareFingerprint, hardwareFingerprint)
                    .last("LIMIT 1 FOR UPDATE"));
            if (fingerprintOwner != null) {
                throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "硬件指纹已绑定其他设备");
            }
            throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "设备入网发生并发冲突，请重试");
        }
        validateExistingIdentity(enrolled, hardwareFingerprint);
        return recoverRecentEnrollment(enrolled, imageVersion, hardwareFingerprint, telemetry, clientIp);
    }

    private EdgeEnrollResponse recoverRecentEnrollment(
            Device device,
            String imageVersion,
            String hardwareFingerprint,
            Map<String, Object> telemetry,
            String clientIp) {
        if (!hasText(device.getDeviceTokenSeed()) || !hasText(device.getDeviceTokenHash())
                || device.getEnrolledAt() == null
                || LocalDateTime.now().isAfter(device.getEnrolledAt().plus(ENROLLMENT_RECOVERY_WINDOW))) {
            throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "设备已完成入网，不能重复签发令牌");
        }

        String rawToken = deriveToken(
                device.getDeviceTokenSeed(),
                device.getSn(),
                hardwareFingerprint);
        if (!sha256(rawToken).equals(device.getDeviceTokenHash())) {
            throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "设备入网凭据状态异常，请管理员重置");
        }

        device.setStatus(1);
        device.setType(2);
        device.setLastHeartbeatTime(LocalDateTime.now());
        device.setIp(limitText(clientIp, 64));
        device.setImageVersion(imageVersion);
        applyTelemetry(device, telemetry);
        deviceMapper.updateById(device);
        return new EdgeEnrollResponse(device.getSn(), device.getId(), device.getBindCode(), rawToken);
    }

    private void validateExistingIdentity(Device device, String hardwareFingerprint) {
        if (hasText(device.getHardwareFingerprint())
                && !hardwareFingerprint.equals(device.getHardwareFingerprint())) {
            throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "设备硬件指纹不匹配");
        }
    }

    @Override
    public Device authenticate(String rawToken) {
        if (!hasText(rawToken) || !TOKEN_PATTERN.matcher(rawToken.trim()).matches()) {
            throw unauthorized();
        }
        String token = rawToken.trim();
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceTokenHash, sha256(token))
                .last("LIMIT 1"));
        if (device == null) {
            throw unauthorized();
        }
        return device;
    }

    @Override
    public void requireOwnedSn(Device device, String requestedSn) {
        String sn = exactText(requestedSn, 64);
        if (device == null || sn == null || !sn.equals(device.getSn())) {
            throw new EdgeDeviceApiException(HttpStatus.FORBIDDEN, "设备身份与请求 SN 不匹配");
        }
    }

    @Override
    public void requireMatchingHardwareFingerprint(Device device, String hardwareFingerprint) {
        String fingerprint = exactText(hardwareFingerprint, 128);
        if (fingerprint != null && device != null && hasText(device.getHardwareFingerprint())
                && !fingerprint.equals(device.getHardwareFingerprint())) {
            throw new EdgeDeviceApiException(HttpStatus.FORBIDDEN, "设备硬件指纹不匹配");
        }
    }

    private void applyTelemetry(Device device, Map<String, Object> telemetry) {
        if (telemetry == null || telemetry.isEmpty()) {
            return;
        }
        device.setCpuUsage(getText(telemetry, "cpu_load", 16));
        device.setMemoryUsage(getText(telemetry, "mem_load", 16));
        device.setCpuModel(getText(telemetry, "cpu_model", 255));
        device.setAgentVersion(getText(telemetry, "agent_version", 64));
        device.setDeviceModel(getText(telemetry, "device_model", 160));
        device.setArchitecture(getText(telemetry, "architecture", 32));
        device.setL4tVersion(getText(telemetry, "l4t_version", 128));
        device.setCudaVersion(getText(telemetry, "cuda_version", 32));
        device.setGpuUsage(getText(telemetry, "gpu_usage", 16));
        device.setGpuTemperature(getDouble(telemetry, "gpu_temperature"));
        device.setPowerWatts(getDouble(telemetry, "power_watts"));
        device.setMemoryTotalMb(getInteger(telemetry, "memory_total_mb"));
    }

    private String generateAvailableBindCode(String sn) {
        String seed = sn;
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "Orin-" + sha256(seed).substring(0, 6).toUpperCase(Locale.ROOT);
            Device owner = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                    .eq(Device::getBindCode, candidate)
                    .last("LIMIT 1"));
            if (owner == null || sn.equals(owner.getSn())) {
                return candidate;
            }
            seed = sn + ':' + randomHex(8);
        }
        throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "设备绑定码生成失败，请重试");
    }

    private String deriveToken(String tokenSeed, String sn, String hardwareFingerprint) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(enrollmentSecret, "HmacSHA256"));
            String context = "juxin-orin-device-token-v2\0"
                    + tokenSeed + '\0' + sn + '\0' + hardwareFingerprint;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(context.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String requireText(String value, String fieldName, int maxLength) {
        String normalized = limitText(value, maxLength);
        if (normalized == null) {
            throw badRequest(fieldName + " is required");
        }
        if (value.trim().length() > maxLength) {
            throw badRequest(fieldName + " is too long");
        }
        return normalized;
    }

    private String getText(Map<String, Object> values, String key, int maxLength) {
        Object value = values.get(key);
        return value == null ? null : limitText(value.toString(), maxLength);
    }

    private Double getDouble(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.toString());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer getInteger(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String exactText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private EdgeDeviceApiException unauthorized() {
        return new EdgeDeviceApiException(HttpStatus.UNAUTHORIZED, "设备令牌缺失或无效");
    }

    private EdgeDeviceApiException badRequest(String message) {
        return new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, message);
    }
}
