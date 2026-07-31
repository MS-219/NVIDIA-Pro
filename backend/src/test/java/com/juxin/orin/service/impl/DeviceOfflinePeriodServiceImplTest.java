package com.juxin.orin.service.impl;

import com.juxin.orin.entity.DeviceOfflinePeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceOfflinePeriodServiceImplTest {

    private static final LocalDateTime DAY_START = LocalDateTime.of(2026, 7, 31, 0, 0);
    private static final LocalDateTime DAY_END = DAY_START.plusDays(1);

    @Test
    void calculateOfflineSecondsClipsAndMergesOverlappingAndDuplicatePeriods() {
        List<DeviceOfflinePeriod> periods = List.of(
                period(DAY_START.minusHours(1), DAY_START.plusHours(2)),
                period(DAY_START.minusHours(1), DAY_START.plusHours(2)),
                period(DAY_START.plusHours(1), DAY_START.plusHours(4)),
                period(DAY_START.plusHours(10), DAY_START.plusHours(12)));

        long seconds = DeviceOfflinePeriodServiceImpl.calculateOfflineSeconds(periods, DAY_START, DAY_END);

        assertEquals(6 * 60 * 60, seconds);
    }

    @Test
    void calculateOfflineSecondsCountsOpenPeriodUntilRangeEnd() {
        DeviceOfflinePeriod openPeriod = period(DAY_START.plusHours(20), null);

        long seconds = DeviceOfflinePeriodServiceImpl.calculateOfflineSeconds(
                List.of(openPeriod), DAY_START, DAY_END);

        assertEquals(4 * 60 * 60, seconds);
    }

    @Test
    void calculateOfflineSecondsIgnoresPeriodsOutsideTheRequestedRange() {
        List<DeviceOfflinePeriod> periods = List.of(
                period(DAY_START.minusHours(2), DAY_START),
                period(DAY_END, DAY_END.plusHours(2)));

        long seconds = DeviceOfflinePeriodServiceImpl.calculateOfflineSeconds(periods, DAY_START, DAY_END);

        assertEquals(0, seconds);
    }

    private DeviceOfflinePeriod period(LocalDateTime offlineStart, LocalDateTime onlineAt) {
        DeviceOfflinePeriod period = new DeviceOfflinePeriod();
        period.setOfflineStart(offlineStart);
        period.setOnlineAt(onlineAt);
        return period;
    }
}
