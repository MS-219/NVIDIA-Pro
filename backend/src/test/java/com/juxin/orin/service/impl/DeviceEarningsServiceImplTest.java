package com.juxin.orin.service.impl;

import com.juxin.orin.entity.Device;
import com.juxin.orin.mapper.DeviceEarningsMapper;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IDeviceOfflinePeriodService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IInviteService;
import com.juxin.orin.service.ISystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceEarningsServiceImplTest {

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
    void dailyBaseEarningsShouldStayInsideConfiguredAmountRange() {
        when(configService.getConfig("earnings.hourlyRate", "2.4")).thenReturn("2.4");
        when(configService.getConfig("earnings.dailyRate", "2.4")).thenReturn("2.4");
        when(configService.getConfig("earnings.dailyMinRate", "2.4")).thenReturn("40");
        when(configService.getConfig("earnings.dailyMaxRate", "2.4")).thenReturn("50");

        LocalDate settlementDate = LocalDate.of(2026, 7, 31);
        BigDecimal first = service.calculateDailyBaseEarnings(7L, settlementDate);
        BigDecimal repeated = service.calculateDailyBaseEarnings(7L, settlementDate);

        assertTrue(first.compareTo(new BigDecimal("40.00")) >= 0);
        assertTrue(first.compareTo(new BigDecimal("50.00")) <= 0);
        assertEquals(first, repeated);
    }

    @Test
    void offlineSecondsEqualToConfiguredLimitShouldStillAllowEarnings() {
        Device device = realDevice();
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 31, 0, 0);
        LocalDateTime dayEnd = dayStart.plusDays(1);
        when(offlinePeriodService.getOfflineSeconds(7L, dayStart, dayEnd)).thenReturn(3 * 60 * 60L);
        when(configService.getConfig("earnings.maxDailyOfflineHours", "24")).thenReturn("3");

        boolean exceeded = service.exceedsDailyOfflineLimit(device, dayStart, dayEnd);

        assertFalse(exceeded);
    }

    @Test
    void offlineSecondsOneSecondOverConfiguredLimitShouldRejectEarnings() {
        Device device = realDevice();
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 31, 0, 0);
        LocalDateTime dayEnd = dayStart.plusDays(1);
        when(offlinePeriodService.getOfflineSeconds(7L, dayStart, dayEnd)).thenReturn(3 * 60 * 60L + 1);
        when(configService.getConfig("earnings.maxDailyOfflineHours", "24")).thenReturn("3");

        boolean exceeded = service.exceedsDailyOfflineLimit(device, dayStart, dayEnd);

        assertTrue(exceeded);
    }

    @Test
    void virtualDeviceShouldBeExemptFromDailyOfflineLimit() {
        Device device = realDevice();
        device.setType(1);

        boolean exceeded = service.exceedsDailyOfflineLimit(
                device,
                LocalDateTime.of(2026, 7, 31, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0));

        assertFalse(exceeded);
        verifyNoInteractions(offlinePeriodService, configService);
    }

    @Test
    void realDeviceWithoutHeartbeatShouldCountAsOfflineForTheWholeDay() {
        Device device = realDevice();
        device.setLastHeartbeatTime(null);
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 31, 0, 0);
        when(configService.getConfig("earnings.maxDailyOfflineHours", "24")).thenReturn("3");

        boolean exceeded = service.exceedsDailyOfflineLimit(device, dayStart, dayStart.plusDays(1));

        assertTrue(exceeded);
        verifyNoInteractions(offlinePeriodService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid", "-1", "25" })
    void invalidOfflineLimitConfigurationShouldFallBackToTwentyFourHours(String configuredValue) {
        Device device = realDevice();
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 31, 0, 0);
        LocalDateTime dayEnd = dayStart.plusDays(1);
        when(offlinePeriodService.getOfflineSeconds(7L, dayStart, dayEnd)).thenReturn(24 * 60 * 60L);
        when(configService.getConfig("earnings.maxDailyOfflineHours", "24")).thenReturn(configuredValue);

        boolean exceeded = service.exceedsDailyOfflineLimit(device, dayStart, dayEnd);

        assertFalse(exceeded);
    }

    private Device realDevice() {
        Device device = new Device();
        device.setId(7L);
        device.setType(2);
        device.setLastHeartbeatTime(LocalDateTime.of(2026, 7, 31, 12, 0));
        return device;
    }
}
