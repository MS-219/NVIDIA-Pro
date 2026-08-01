package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.Device;
import com.juxin.orin.mapper.DeviceMapper;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.util.IpUtil;
import com.juxin.orin.util.DeviceBindCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements IDeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceServiceImpl.class);

    @Autowired
    private IpUtil ipUtil;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    @Autowired
    private com.juxin.orin.service.IDeviceOfflinePeriodService offlinePeriodService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.juxin.orin.service.IAppUserService appUserService;

    @Override
    public Device handleHeartbeat(String sn, String ip, String cpuUsage, String memoryUsage) {
        // 1. 精确匹配 SN 查找设备
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getSn, sn);
        Device device = this.getOne(wrapper);

        String rawLocation = resolveLocation(ip);

        if (log.isDebugEnabled()) {
            if (device != null) {
                log.debug("[Heartbeat] SN={}, 收到IP={}, 解析位置={}, 数据库旧IP={}, 数据库旧位置={}",
                        sn, ip, rawLocation, device.getIp(), device.getLocation());
            } else {
                log.debug("[Heartbeat] SN={} (新设备), 收到IP={}, 解析位置={}", sn, ip, rawLocation);
            }
        }

        // 2. 如果精确匹配失败，尝试通过 MAC 地址核心部分模糊匹配
        if (device == null && sn != null && sn.contains("_")) {
            String macPart = sn.substring(sn.lastIndexOf("_") + 1);
            if (macPart.length() >= 6) {
                Device existingDevice = this.lambdaQuery()
                        .like(Device::getSn, "%" + macPart)
                        .last("LIMIT 1")
                        .one();

                if (existingDevice != null) {
                    existingDevice.setSn(sn);
                    device = existingDevice;
                }
            }
        }

        if (device == null) {
            // 3. 自动注册新设备
            device = new Device();
            device.setSn(sn);
            device.setBindCode(generateBindCodeFromSn(sn));
            device.setCreateTime(LocalDateTime.now());
            device.setStatus(1);
            device.setLastHeartbeatTime(LocalDateTime.now());
            device.setIp(ip);
            updateDeviceIpInfo(device, rawLocation);
            device.setHashrate(0);
            device.setCpuUsage(cpuUsage);
            device.setMemoryUsage(memoryUsage);
            this.save(device);
        } else {
            // 4. 更新心跳时间和状态
            LocalDateTime heartbeatAt = LocalDateTime.now();
            offlinePeriodService.recordHeartbeat(device, getOfflineThresholdSeconds(), heartbeatAt);

            if (device.getUserId() == null || device.getBindCode() == null || device.getBindCode().isEmpty()) {
                device.setBindCode(generateBindCodeFromSn(device.getSn()));
            }

            if (device.getUserId() != null && (device.getBusinessId() == null || device.getBusinessId().isEmpty())) {
                String autoAssign = configService.getConfig("device.autoAssignBusiness", "true");
                if (Boolean.parseBoolean(autoAssign)) {
                    device.setBusinessId("YW" + System.currentTimeMillis());
                }
            }

            device.setStatus(1);
            device.setLastHeartbeatTime(heartbeatAt);
            device.setIp(ip);
            device.setCpuUsage(cpuUsage);
            device.setMemoryUsage(memoryUsage);
            updateDeviceIpInfo(device, rawLocation);
        }

        this.updateById(device);
        return device;
    }

    /**
     * 解析并更新设备的地理位置和运营商信息
     */
    private void updateDeviceIpInfo(Device device, String rawLocation) {
        if (rawLocation == null || rawLocation.isEmpty() || rawLocation.equals("未知位置")
                || rawLocation.equals("局域网/本地")) {
            device.setLocation(rawLocation != null && !rawLocation.isEmpty() ? rawLocation : "未知位置");
            return;
        }

        String cleanedLocation = rawLocation;
        String detectedCarrier = null;

        // 1. 尝试从末尾提取关键词
        String[] keywords = { "电信", "移动", "联通", "广电", "移通", "铁通", "长城", "网通" };
        for (String kw : keywords) {
            if (rawLocation.contains(kw)) {
                int index = rawLocation.indexOf(kw);
                cleanedLocation = rawLocation.substring(0, index).trim();
                detectedCarrier = rawLocation.substring(index).trim();
                break;
            }
        }

        // 2. 如果没匹配到关键词但有空格，尝试按空格拆分
        if (detectedCarrier == null && rawLocation.contains(" ")) {
            String[] parts = rawLocation.split("\\s+");
            if (parts.length >= 2) {
                detectedCarrier = parts[parts.length - 1];
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0)
                        sb.append(" ");
                    sb.append(parts[i]);
                }
                cleanedLocation = sb.toString().trim();
            }
        }

        // 3. 标准化运营商名称
        if (detectedCarrier != null) {
            if (detectedCarrier.contains("移动") || detectedCarrier.contains("移通")) {
                device.setCarrier("中国移动");
            } else if (detectedCarrier.contains("电信")) {
                device.setCarrier("中国电信");
            } else if (detectedCarrier.contains("联通")) {
                device.setCarrier("中国联通");
            } else if (detectedCarrier.contains("广电")) {
                device.setCarrier("中国广电");
            } else {
                device.setCarrier(detectedCarrier);
            }
        }

        // 4. 更新地理位置 (再次清洗残留在位置字符串里的关键词)
        for (String kw : keywords) {
            if (cleanedLocation.endsWith(kw)) {
                cleanedLocation = cleanedLocation.substring(0, cleanedLocation.length() - kw.length()).trim();
            }
        }
        device.setLocation(cleanedLocation);
    }

    private String resolveLocation(String ip) {
        return ipUtil.getLocation(ip);
    }

    @Override
    public boolean bindDevice(String sn, String code, Long userId) {
        return bindDevice(sn, code, userId, null);
    }

    @Override
    public boolean bindDevice(String sn, String code, Long userId, Long merchantId) {
        Device device = null;
        if (code != null && !code.isEmpty()) {
            code = code.trim();
            device = this.lambdaQuery().eq(Device::getBindCode, code).one();
            if (device == null)
                device = this.lambdaQuery().eq(Device::getBindCode, code.toUpperCase()).one();
            if (device == null)
                device = this.lambdaQuery().eq(Device::getBindCode, code.toLowerCase()).one();
            
            // 如果通过绑定码未找到，尝试将 code 作为 SN 进行匹配，与 queryByCode 的降级匹配逻辑保持一致
            if (device == null) {
                device = this.lambdaQuery().eq(Device::getSn, code).one();
            }
        }
        if (device == null && sn != null && !sn.isEmpty()) {
            device = this.lambdaQuery().eq(Device::getSn, sn).one();
        }

        if (device == null || device.getUserId() != null) {
            return false;
        }
        if (device.getType() != null && device.getType() == 1) {
            return false;
        }

        // 设备一旦归属代理商，不允许被其他代理商重新绑定或转移
        if (device.getMerchantId() != null) {
            if (merchantId == null || !device.getMerchantId().equals(merchantId)) {
                return false;
            }
        }

        device.setUserId(userId);
        if (device.getMerchantId() == null) {
            device.setMerchantId(merchantId); // 首次绑定时设置归属代理商
        }
        LocalDateTime bindTime = LocalDateTime.now();
        device.setBindTime(bindTime);
        device.setLastPayTime(bindTime);

        String autoAssign = configService.getConfig("device.autoAssignBusiness", "true");
        if (Boolean.parseBoolean(autoAssign)) {
            device.setBusinessId("YW" + System.currentTimeMillis());
        }

        String initialHashrateStr = configService.getConfig("device.initialHashrate", "100");
        device.setHashrate(Integer.parseInt(initialHashrateStr));

        boolean success = this.updateById(device);
        if (success) {
            try {
                appUserService.updateAllUserLevels();
            } catch (Exception e) {
            }
        }
        return success;
    }

    private int getOfflineThresholdSeconds() {
        try {
            return Math.max(1, Integer.parseInt(configService.getConfig("device.offlineThreshold", "180")));
        } catch (NumberFormatException ignored) {
            return 180;
        }
    }

    private String generateBindCodeFromSn(String sn) {
        if (sn == null)
            return null;
        return DeviceBindCodeGenerator.fromSeed(sn);
    }
}
