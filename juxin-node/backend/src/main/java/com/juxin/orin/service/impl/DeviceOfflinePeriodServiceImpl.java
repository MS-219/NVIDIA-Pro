package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceOfflinePeriod;
import com.juxin.orin.mapper.DeviceOfflinePeriodMapper;
import com.juxin.orin.service.IDeviceOfflinePeriodService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeviceOfflinePeriodServiceImpl
        extends ServiceImpl<DeviceOfflinePeriodMapper, DeviceOfflinePeriod>
        implements IDeviceOfflinePeriodService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureOfflinePeriod(Device device, int offlineThresholdSeconds, LocalDateTime now) {
        if (device == null) {
            return;
        }
        ensureOfflinePeriods(List.of(device), offlineThresholdSeconds, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureOfflinePeriods(
            Collection<Device> devices,
            int offlineThresholdSeconds,
            LocalDateTime now) {
        if (devices == null || devices.isEmpty() || now == null) {
            return;
        }

        long effectiveThresholdSeconds = Math.max(1, offlineThresholdSeconds);
        Map<Long, LocalDateTime> candidateStarts = new LinkedHashMap<>();
        for (Device device : devices) {
            if (!isTrackable(device) || device.getLastHeartbeatTime() == null) {
                continue;
            }
            LocalDateTime offlineStart = device.getLastHeartbeatTime().plusSeconds(effectiveThresholdSeconds);
            if (offlineStart.isAfter(now)) {
                continue;
            }
            candidateStarts.merge(
                    device.getId(),
                    offlineStart,
                    (existing, candidate) -> candidate.isBefore(existing) ? candidate : existing);
        }
        if (candidateStarts.isEmpty()) {
            return;
        }

        Set<Long> devicesWithOpenPeriod = lambdaQuery()
                .select(DeviceOfflinePeriod::getDeviceId)
                .in(DeviceOfflinePeriod::getDeviceId, candidateStarts.keySet())
                .isNull(DeviceOfflinePeriod::getOnlineAt)
                .list()
                .stream()
                .map(DeviceOfflinePeriod::getDeviceId)
                .collect(Collectors.toSet());

        candidateStarts.forEach((deviceId, offlineStart) -> {
            if (!devicesWithOpenPeriod.contains(deviceId)) {
                savePeriod(deviceId, offlineStart, null, now);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordHeartbeat(Device device, int offlineThresholdSeconds, LocalDateTime heartbeatAt) {
        if (!isTrackable(device) || device.getLastHeartbeatTime() == null || heartbeatAt == null) {
            return;
        }

        if (closeExistingPeriodsAtFirstHeartbeat(device.getId(), heartbeatAt)) {
            return;
        }

        LocalDateTime offlineStart = device.getLastHeartbeatTime()
                .plusSeconds(Math.max(1, offlineThresholdSeconds));
        if (offlineStart.isBefore(heartbeatAt)) {
            savePeriod(device.getId(), offlineStart, heartbeatAt, heartbeatAt);
        }
    }

    @Override
    public long getOfflineSeconds(Long deviceId, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (deviceId == null || rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
            return 0;
        }
        List<DeviceOfflinePeriod> periods = lambdaQuery()
                .eq(DeviceOfflinePeriod::getDeviceId, deviceId)
                .lt(DeviceOfflinePeriod::getOfflineStart, rangeEnd)
                .and(wrapper -> wrapper.isNull(DeviceOfflinePeriod::getOnlineAt)
                        .or()
                        .gt(DeviceOfflinePeriod::getOnlineAt, rangeStart))
                .orderByAsc(DeviceOfflinePeriod::getOfflineStart)
                .list();
        return calculateOfflineSeconds(periods, rangeStart, rangeEnd);
    }

    static long calculateOfflineSeconds(
            List<DeviceOfflinePeriod> periods,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        if (periods == null || periods.isEmpty()) {
            return 0;
        }

        List<DeviceOfflinePeriod> sorted = periods.stream()
                .filter(period -> period != null && period.getOfflineStart() != null)
                .sorted(Comparator.comparing(DeviceOfflinePeriod::getOfflineStart))
                .toList();

        LocalDateTime mergedStart = null;
        LocalDateTime mergedEnd = null;
        long totalSeconds = 0;
        for (DeviceOfflinePeriod period : sorted) {
            LocalDateTime start = period.getOfflineStart().isBefore(rangeStart)
                    ? rangeStart
                    : period.getOfflineStart();
            LocalDateTime periodEnd = period.getOnlineAt() == null ? rangeEnd : period.getOnlineAt();
            LocalDateTime end = periodEnd.isAfter(rangeEnd) ? rangeEnd : periodEnd;
            if (!end.isAfter(start)) {
                continue;
            }

            if (mergedEnd != null && !start.isAfter(mergedEnd)) {
                if (end.isAfter(mergedEnd)) {
                    mergedEnd = end;
                }
                continue;
            }
            if (mergedStart != null) {
                totalSeconds += Duration.between(mergedStart, mergedEnd).getSeconds();
            }
            mergedStart = start;
            mergedEnd = end;
        }
        if (mergedStart != null) {
            totalSeconds += Duration.between(mergedStart, mergedEnd).getSeconds();
        }
        return totalSeconds;
    }

    private boolean closeExistingPeriodsAtFirstHeartbeat(Long deviceId, LocalDateTime heartbeatAt) {
        return lambdaUpdate()
                .eq(DeviceOfflinePeriod::getDeviceId, deviceId)
                .le(DeviceOfflinePeriod::getOfflineStart, heartbeatAt)
                .and(wrapper -> wrapper.isNull(DeviceOfflinePeriod::getOnlineAt)
                        .or()
                        .gt(DeviceOfflinePeriod::getOnlineAt, heartbeatAt))
                .set(DeviceOfflinePeriod::getOnlineAt, heartbeatAt)
                .set(DeviceOfflinePeriod::getUpdateTime, heartbeatAt)
                .update();
    }

    private void savePeriod(
            Long deviceId,
            LocalDateTime offlineStart,
            LocalDateTime onlineAt,
            LocalDateTime now) {
        DeviceOfflinePeriod period = new DeviceOfflinePeriod();
        period.setDeviceId(deviceId);
        period.setOfflineStart(offlineStart);
        period.setOnlineAt(onlineAt);
        period.setCreateTime(now);
        period.setUpdateTime(now);
        try {
            save(period);
        } catch (DuplicateKeyException ignored) {
            // 心跳与离线扫描可能同时插入同一起点；心跳必须确保已有区间被关闭。
            if (onlineAt != null) {
                lambdaUpdate()
                        .eq(DeviceOfflinePeriod::getDeviceId, deviceId)
                        .eq(DeviceOfflinePeriod::getOfflineStart, offlineStart)
                        .and(wrapper -> wrapper.isNull(DeviceOfflinePeriod::getOnlineAt)
                                .or()
                                .gt(DeviceOfflinePeriod::getOnlineAt, onlineAt))
                        .set(DeviceOfflinePeriod::getOnlineAt, onlineAt)
                        .set(DeviceOfflinePeriod::getUpdateTime, now)
                        .update();
            }
        }
    }

    private boolean isTrackable(Device device) {
        return device != null
                && device.getId() != null
                && (device.getType() == null || device.getType() != 1);
    }
}
