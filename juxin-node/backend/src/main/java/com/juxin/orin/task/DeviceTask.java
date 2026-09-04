package com.juxin.orin.task;

import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class DeviceTask {

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    @Autowired
    private com.juxin.orin.service.IDeviceOfflineLogService offlineLogService;

    @Autowired
    private com.juxin.orin.service.IDeviceOfflinePeriodService offlinePeriodService;

    /**
     * 每分钟检查一次设备状态
     */
    @Scheduled(fixedRate = 60000)
    @Transactional(rollbackFor = Exception.class)
    public void checkDeviceStatus() {
        int offlineSeconds = getOfflineThresholdSeconds();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutThreshold = now.minusSeconds(offlineSeconds);

        // 1. 查找当前在线但已超时的实体/边缘设备，排除虚拟设备(type=1)
        java.util.List<Device> timeoutDevices = deviceService.lambdaQuery()
                .eq(Device::getStatus, 1)
                .and(w -> w.isNull(Device::getType).or().ne(Device::getType, 1))
                .lt(Device::getLastHeartbeatTime, timeoutThreshold)
                .list();

        java.util.List<com.juxin.orin.entity.DeviceOfflineLog> logs = new java.util.ArrayList<>();
        for (Device device : timeoutDevices) {
            boolean markedOffline = deviceService.lambdaUpdate()
                    .eq(Device::getId, device.getId())
                    .eq(Device::getStatus, 1)
                    .lt(Device::getLastHeartbeatTime, timeoutThreshold)
                    .set(Device::getStatus, 0)
                    .update();
            if (!markedOffline) {
                continue;
            }

            com.juxin.orin.entity.DeviceOfflineLog log = new com.juxin.orin.entity.DeviceOfflineLog();
            log.setDeviceId(device.getId());
            log.setSn(device.getSn());
            log.setBindCode(device.getBindCode());
            log.setOfflineTime(device.getLastHeartbeatTime().plusSeconds(offlineSeconds));
            log.setLastHeartbeatTime(device.getLastHeartbeatTime());
            log.setReason("心跳超时 ( > " + offlineSeconds + "秒)");
            log.setCreateTime(now);
            logs.add(log);
        }

        if (!logs.isEmpty()) {
            offlineLogService.saveBatch(logs);
        }

        // 为所有离线设备批量补建开放区间，避免逐台查询形成每分钟 N+1。
        java.util.List<Device> offlineDevices = deviceService.lambdaQuery()
                .select(Device::getId, Device::getType, Device::getLastHeartbeatTime)
                .eq(Device::getStatus, 0)
                .and(wrapper -> wrapper.isNull(Device::getType).or().ne(Device::getType, 1))
                .isNotNull(Device::getLastHeartbeatTime)
                .list();
        offlinePeriodService.ensureOfflinePeriods(offlineDevices, offlineSeconds, now);
    }

    private int getOfflineThresholdSeconds() {
        try {
            return Math.max(1, Integer.parseInt(configService.getConfig("device.offlineThreshold", "180")));
        } catch (NumberFormatException ignored) {
            return 180;
        }
    }
}
