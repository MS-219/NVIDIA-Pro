package com.juxin.orin.task;

import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DeviceTask {

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    @Autowired
    private com.juxin.orin.service.IDeviceOfflineLogService offlineLogService;

    /**
     * 每分钟检查一次设备状态
     */
    @Scheduled(fixedRate = 60000)
    public void checkDeviceStatus() {
        // 从配置读取离线阈值（秒），默认 120 秒
        int offlineSeconds = Integer.parseInt(configService.getConfig("device.offlineThreshold", "180"));
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusSeconds(offlineSeconds);

        // 1. 查找当前在线但已超时的实体/边缘设备，排除虚拟设备(type=1)
        java.util.List<Device> timeoutDevices = deviceService.lambdaQuery()
                .eq(Device::getStatus, 1)
                .and(w -> w.isNull(Device::getType).or().ne(Device::getType, 1))
                .lt(Device::getLastHeartbeatTime, timeoutThreshold)
                .list();

        if (timeoutDevices.isEmpty()) {
            return;
        }

        // 2. 更新状态为离线
        java.util.List<Long> ids = timeoutDevices.stream().map(Device::getId)
                .collect(java.util.stream.Collectors.toList());
        deviceService.lambdaUpdate()
                .in(Device::getId, ids)
                .set(Device::getStatus, 0)
                .update();

        // 3. 记录离线日志
        java.util.List<com.juxin.orin.entity.DeviceOfflineLog> logs = timeoutDevices.stream().map(d -> {
            com.juxin.orin.entity.DeviceOfflineLog log = new com.juxin.orin.entity.DeviceOfflineLog();
            log.setDeviceId(d.getId());
            log.setSn(d.getSn());
            log.setBindCode(d.getBindCode());
            log.setOfflineTime(LocalDateTime.now());
            log.setLastHeartbeatTime(d.getLastHeartbeatTime());
            log.setReason("心跳超时 ( > " + offlineSeconds + "秒)");
            log.setCreateTime(LocalDateTime.now());
            return log;
        }).collect(java.util.stream.Collectors.toList());

        offlineLogService.saveBatch(logs);
    }
}
