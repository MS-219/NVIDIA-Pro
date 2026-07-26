package com.juxin.orin.service.impl;

import com.juxin.orin.entity.Device;
import com.juxin.orin.mapper.DeviceEarningsMapper;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IInviteService;
import com.juxin.orin.service.ISystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
