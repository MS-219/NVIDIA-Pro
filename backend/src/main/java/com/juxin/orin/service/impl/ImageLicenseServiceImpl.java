package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.ImageLicense;
import com.juxin.orin.entity.ImageLicenseActivation;
import com.juxin.orin.entity.SysUser;
import com.juxin.orin.mapper.ImageLicenseActivationMapper;
import com.juxin.orin.mapper.ImageLicenseMapper;
import com.juxin.orin.service.IImageLicenseService;
import com.juxin.orin.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Service
public class ImageLicenseServiceImpl extends ServiceImpl<ImageLicenseMapper, ImageLicense>
        implements IImageLicenseService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private ImageLicenseActivationMapper activationMapper;

    @Autowired
    private ISysUserService sysUserService;

    @Override
    public ImageLicense createLicense(Map<String, Object> params, String createdBy) {
        String name = getText(params, "name", 100);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("镜像名称不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        ImageLicense license = new ImageLicense();
        license.setLicenseKey(generateUniqueLicenseKey());
        license.setName(name);
        license.setImageVersion(getText(params, "imageVersion", 64));
        license.setStatus("active");
        license.setRemark(getText(params, "remark", 255));
        license.setCreatedBy(limitText(createdBy, 64));
        license.setFactoryUsername(validateFactoryUsername(getText(params, "factoryUsername", 64)));
        license.setCreateTime(now);
        license.setUpdateTime(now);
        this.save(license);
        license.setActivationCount(0L);
        return license;
    }

    @Override
    public IPage<ImageLicense> listLicenses(Integer page, Integer size, String status, String keyword) {
        Page<ImageLicense> pageParam = new Page<>(safePage(page), safeSize(size));
        LambdaQueryWrapper<ImageLicense> wrapper = new LambdaQueryWrapper<>();

        String normalizedStatus = status == null ? null : status.trim().toLowerCase(Locale.ROOT);
        if ("active".equals(normalizedStatus) || "revoked".equals(normalizedStatus)) {
            wrapper.eq(ImageLicense::getStatus, normalizedStatus);
        }

        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && !normalizedKeyword.isEmpty()) {
            wrapper.and(w -> w.like(ImageLicense::getName, normalizedKeyword)
                    .or().like(ImageLicense::getLicenseKey, normalizedKeyword)
                    .or().like(ImageLicense::getImageVersion, normalizedKeyword));
        }

        wrapper.orderByDesc(ImageLicense::getCreateTime);
        IPage<ImageLicense> result = this.page(pageParam, wrapper);
        fillActivationStats(result);
        return result;
    }

    @Override
    public boolean revokeLicense(Long id) {
        if (id == null) {
            return false;
        }
        ImageLicense license = this.getById(id);
        if (license == null) {
            return false;
        }
        license.setStatus("revoked");
        license.setRevokedAt(LocalDateTime.now());
        license.setUpdateTime(LocalDateTime.now());
        return this.updateById(license);
    }

    @Override
    public ImageLicense assignFactory(Long id, String factoryUsername) {
        if (id == null) {
            throw new IllegalArgumentException("镜像授权ID不能为空");
        }
        ImageLicense license = this.getById(id);
        if (license == null) {
            throw new IllegalArgumentException("镜像授权不存在");
        }

        license.setFactoryUsername(validateFactoryUsername(getNullableText(factoryUsername, 64)));
        license.setUpdateTime(LocalDateTime.now());
        this.updateById(license);
        fillActivationStats(new Page<ImageLicense>().setRecords(java.util.List.of(license)));
        return license;
    }

    @Override
    public String validateNewDeviceLicense(String licenseKey) {
        String normalizedKey = normalizeLicenseKey(licenseKey);
        if (normalizedKey == null) {
            return "镜像未授权：缺少镜像授权码";
        }
        ImageLicense license = this.lambdaQuery()
                .eq(ImageLicense::getLicenseKey, normalizedKey)
                .one();
        if (license == null) {
            return "镜像未授权：授权码不存在";
        }
        if (!"active".equals(license.getStatus())) {
            return "镜像授权已销毁，禁止新设备接入";
        }
        return null;
    }

    @Override
    public void recordActivation(ImageLicense license, Device device, String hardwareFingerprint,
                                 String agentVersion, String imageVersion, String ip, String cpuModel) {
        if (license == null || device == null || device.getSn() == null || device.getSn().isBlank()) {
            return;
        }

        String normalizedKey = normalizeLicenseKey(license.getLicenseKey());
        if (normalizedKey == null) {
            return;
        }

        String deviceSn = limitText(device.getSn(), 64);
        LocalDateTime now = LocalDateTime.now();
        ImageLicenseActivation activation = activationMapper.selectOne(new LambdaQueryWrapper<ImageLicenseActivation>()
                .eq(ImageLicenseActivation::getLicenseKey, normalizedKey)
                .eq(ImageLicenseActivation::getDeviceSn, deviceSn)
                .last("LIMIT 1"));

        if (activation == null) {
            activation = new ImageLicenseActivation();
            activation.setLicenseId(license.getId());
            activation.setLicenseKey(normalizedKey);
            activation.setDeviceSn(deviceSn);
            activation.setFirstSeenAt(now);
        }

        activation.setDeviceId(device.getId());
        activation.setHardwareFingerprint(limitText(hardwareFingerprint, 128));
        activation.setAgentVersion(limitText(agentVersion, 64));
        activation.setImageVersion(limitText(imageVersion, 64));
        activation.setIp(limitText(ip, 64));
        activation.setCpuModel(limitText(cpuModel, 255));
        activation.setLastSeenAt(now);

        if (activation.getId() == null) {
            activationMapper.insert(activation);
        } else {
            activationMapper.updateById(activation);
        }
    }

    @Override
    public IPage<ImageLicenseActivation> listActivations(Long licenseId, Integer page, Integer size) {
        LambdaQueryWrapper<ImageLicenseActivation> wrapper = new LambdaQueryWrapper<>();
        if (licenseId != null) {
            wrapper.eq(ImageLicenseActivation::getLicenseId, licenseId);
        }
        wrapper.orderByDesc(ImageLicenseActivation::getLastSeenAt);
        return activationMapper.selectPage(new Page<>(safePage(page), safeSize(size)), wrapper);
    }

    private void fillActivationStats(IPage<ImageLicense> page) {
        if (page == null || page.getRecords() == null) {
            return;
        }
        for (ImageLicense license : page.getRecords()) {
            Long count = activationMapper.selectCount(new LambdaQueryWrapper<ImageLicenseActivation>()
                    .eq(ImageLicenseActivation::getLicenseKey, license.getLicenseKey()));
            license.setActivationCount(count);

            ImageLicenseActivation last = activationMapper.selectOne(new LambdaQueryWrapper<ImageLicenseActivation>()
                    .eq(ImageLicenseActivation::getLicenseKey, license.getLicenseKey())
                    .orderByDesc(ImageLicenseActivation::getLastSeenAt)
                    .last("LIMIT 1"));
            if (last != null) {
                license.setLastSeenAt(last.getLastSeenAt());
            }
        }
    }

    private String generateUniqueLicenseKey() {
        for (int i = 0; i < 10; i++) {
            String key = "IMG-" + DATE_FORMATTER.format(LocalDateTime.now()) + "-" + randomHex(12);
            if (this.lambdaQuery().eq(ImageLicense::getLicenseKey, key).count() == 0) {
                return key;
            }
        }
        throw new IllegalStateException("镜像授权码生成失败，请重试");
    }

    private String randomHex(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytesLength * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02X", b));
        }
        return builder.toString();
    }

    private int safePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private String getText(Map<String, Object> params, String key, int maxLength) {
        if (params == null || params.get(key) == null) {
            return null;
        }
        return limitText(params.get(key).toString().trim(), maxLength);
    }

    private String getNullableText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return limitText(value.trim(), maxLength);
    }

    private String validateFactoryUsername(String factoryUsername) {
        if (factoryUsername == null || factoryUsername.isBlank()) {
            return null;
        }
        SysUser factoryUser = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, factoryUsername)
                .last("LIMIT 1")
                .one();
        if (factoryUser == null) {
            throw new IllegalArgumentException("工厂账号不存在");
        }
        if (!"factory".equals(factoryUser.getRole())) {
            throw new IllegalArgumentException("绑定账号必须是工厂角色");
        }
        return factoryUsername;
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String normalizeLicenseKey(String licenseKey) {
        if (licenseKey == null || licenseKey.isBlank()) {
            return null;
        }
        return limitText(licenseKey, 64).toUpperCase(Locale.ROOT);
    }
}
