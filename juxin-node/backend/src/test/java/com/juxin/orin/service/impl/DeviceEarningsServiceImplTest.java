package com.juxin.orin.service.impl;

import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceOfflinePeriod;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.mapper.DeviceEarningsMapper;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IDeviceOfflinePeriodService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IInviteService;
import com.juxin.orin.service.ISystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceEarningsServiceImplTest {

    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 7, 31);

    @Mock
    private IAppUserService appUserService;

    @Mock
    private IDeviceService deviceService;

    @Mock
    private ISystemConfigService configService;

    @Mock
    private IInviteService inviteService;

    @Mock
    private DeviceEarningsMapper earningsMapper;

    @Mock
    private IDeviceOfflinePeriodService offlinePeriodService;

    @InjectMocks
    private DeviceEarningsServiceImpl service;

    @Test
    void deletedUserDeviceShouldAdvanceSettlementTimeWithoutGeneratingAssets() {
        Device device = new Device();
        device.setId(7L);
        device.setUserId(100001L);
        device.setHashrate(500);
        LocalDateTime settlementTime = LocalDateTime.of(2026, 7, 16, 12, 0);

        when(appUserService.getById(100001L)).thenReturn(null);

        service.processDeviceEarnings(device, settlementTime, LocalDate.of(2026, 7, 16));

        assertEquals(settlementTime, device.getLastPayTime());
        assertEquals(500, device.getHashrate());
        verify(deviceService).updateById(device);
        verifyNoInteractions(configService, inviteService, earningsMapper);
    }

    @Test
    void hourlyBaseEarningsShouldStayInsideConfiguredAmountRange() {
        when(configService.getConfig("earnings.hourlyMinRate")).thenReturn("0.40");
        when(configService.getConfig("earnings.hourlyMaxRate")).thenReturn("0.50");

        BigDecimal first = service.calculateHourlyBaseEarnings(7L, SETTLEMENT_DATE, 0);
        BigDecimal repeated = service.calculateHourlyBaseEarnings(7L, SETTLEMENT_DATE, 0);

        assertTrue(first.compareTo(new BigDecimal("0.4000")) >= 0);
        assertTrue(first.compareTo(new BigDecimal("0.5000")) <= 0);
        assertEquals(first, repeated);
    }

    @Test
    void userHourlyRangeShouldOverrideGlobalRange() {
        AppUser user = new AppUser();
        user.setDailyEarningsMin(new BigDecimal("0.70"));
        user.setDailyEarningsMax(new BigDecimal("0.80"));

        BigDecimal earnings = service.calculateHourlyBaseEarnings(7L, SETTLEMENT_DATE, 0, user);

        assertTrue(earnings.compareTo(new BigDecimal("0.7000")) >= 0);
        assertTrue(earnings.compareTo(new BigDecimal("0.8000")) <= 0);
        verifyNoInteractions(configService);
    }

    @Test
    void incompleteUserHourlyRangeShouldFallBackToGlobalRange() {
        AppUser user = new AppUser();
        user.setDailyEarningsMin(new BigDecimal("0.70"));
        when(configService.getConfig("earnings.hourlyMinRate")).thenReturn("0.40");
        when(configService.getConfig("earnings.hourlyMaxRate")).thenReturn("0.50");

        BigDecimal earnings = service.calculateHourlyBaseEarnings(7L, SETTLEMENT_DATE, 0, user);

        assertTrue(earnings.compareTo(new BigDecimal("0.4000")) >= 0);
        assertTrue(earnings.compareTo(new BigDecimal("0.5000")) <= 0);
    }

    @Test
    void virtualDeviceShouldCountTwentyFourOnlineHours() {
        Device device = realDevice();
        device.setType(1);

        int onlineHours = service.countFullyOnlineHours(device, SETTLEMENT_DATE);

        assertEquals(24, onlineHours);
        verifyNoInteractions(offlinePeriodService);
    }

    @Test
    void realDeviceWithoutHeartbeatShouldCountZeroOnlineHours() {
        Device device = realDevice();
        device.setLastHeartbeatTime(null);

        int onlineHours = service.countFullyOnlineHours(device, SETTLEMENT_DATE);

        assertEquals(0, onlineHours);
        verifyNoInteractions(offlinePeriodService);
    }

    @Test
    void realDeviceFullyOnlineDayShouldCountTwentyFourHours() {
        Device device = realDevice();
        when(offlinePeriodService.getOfflinePeriods(
                7L, SETTLEMENT_DATE.atStartOfDay(), SETTLEMENT_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of());

        int onlineHours = service.countFullyOnlineHours(device, SETTLEMENT_DATE);

        assertEquals(24, onlineHours);
    }

    @Test
    void wholeOfflineHourShouldNotAccrue() {
        Device device = realDevice();
        LocalDateTime dayStart = SETTLEMENT_DATE.atStartOfDay();
        when(offlinePeriodService.getOfflinePeriods(
                7L, dayStart, SETTLEMENT_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(offlinePeriod(10, 0, 11, 0)));

        int onlineHours = service.countFullyOnlineHours(device, SETTLEMENT_DATE);

        assertEquals(23, onlineHours);
    }

    @Test
    void partialOfflineHourShouldNotAccrue() {
        Device device = realDevice();
        LocalDateTime dayStart = SETTLEMENT_DATE.atStartOfDay();
        when(offlinePeriodService.getOfflinePeriods(
                7L, dayStart, SETTLEMENT_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(offlinePeriod(10, 30, 11, 30)));

        int onlineHours = service.countFullyOnlineHours(device, SETTLEMENT_DATE);

        assertEquals(22, onlineHours);
    }

    @Test
    void hourlyTotalShouldSumOnlyFullyOnlineHours() {
        Device device = realDevice();
        LocalDateTime dayStart = SETTLEMENT_DATE.atStartOfDay();
        when(offlinePeriodService.getOfflinePeriods(
                7L, dayStart, SETTLEMENT_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(offlinePeriod(10, 0, 11, 0)));
        when(configService.getConfig("earnings.hourlyMinRate")).thenReturn("0.40");
        when(configService.getConfig("earnings.hourlyMaxRate")).thenReturn("0.40");

        BigDecimal total = service.calculateHourlyTotalEarnings(device, SETTLEMENT_DATE, null);

        // 24 小时中 1 小时离线，23 小时 × 0.40 = 9.20
        assertEquals(new BigDecimal("9.20"), total);
    }

    private Device realDevice() {
        Device device = new Device();
        device.setId(7L);
        device.setType(2);
        device.setLastHeartbeatTime(SETTLEMENT_DATE.atTime(12, 0));
        return device;
    }

    private DeviceOfflinePeriod offlinePeriod(int startHour, int startMinute, int endHour, int endMinute) {
        DeviceOfflinePeriod period = new DeviceOfflinePeriod();
        period.setDeviceId(7L);
        period.setOfflineStart(SETTLEMENT_DATE.atTime(startHour, startMinute));
        period.setOnlineAt(SETTLEMENT_DATE.atTime(endHour, endMinute));
        return period;
    }
}
