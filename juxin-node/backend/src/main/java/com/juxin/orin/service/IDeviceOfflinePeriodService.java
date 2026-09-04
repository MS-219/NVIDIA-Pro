package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceOfflinePeriod;

import java.time.LocalDateTime;
import java.util.Collection;

public interface IDeviceOfflinePeriodService extends IService<DeviceOfflinePeriod> {

    void ensureOfflinePeriod(Device device, int offlineThresholdSeconds, LocalDateTime now);

    void ensureOfflinePeriods(Collection<Device> devices, int offlineThresholdSeconds, LocalDateTime now);

    void recordHeartbeat(Device device, int offlineThresholdSeconds, LocalDateTime heartbeatAt);

    long getOfflineSeconds(Long deviceId, LocalDateTime rangeStart, LocalDateTime rangeEnd);
}
