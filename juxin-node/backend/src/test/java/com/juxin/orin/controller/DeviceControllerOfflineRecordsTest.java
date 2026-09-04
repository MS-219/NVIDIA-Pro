package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceOfflineLog;
import com.juxin.orin.service.IDeviceOfflineLogService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerOfflineRecordsTest {

    @Mock
    private IDeviceService deviceService;

    @Mock
    private IDeviceOfflineLogService offlineLogService;

    @InjectMocks
    private DeviceController controller;

    @Test
    void rejectsAUserWhoDoesNotOwnTheDevice() {
        Device device = new Device();
        device.setId(12L);
        device.setUserId(8L);
        when(deviceService.getById(12L)).thenReturn(device);
        String token = JwtUtil.generateToken(7L, "user-7", "app");

        Result<Object> result = controller.offlineRecords(12L, 1, 20, "Bearer " + token);

        assertEquals(500, result.getCode());
        assertEquals("无权限查看此设备的离线记录", result.getMsg());
        verify(offlineLogService, never()).page(any(), any());
    }

    @Test
    void returnsOwnedDeviceRecordsAndCapsPageSize() {
        Device device = new Device();
        device.setId(12L);
        device.setUserId(7L);
        when(deviceService.getById(12L)).thenReturn(device);
        Page<DeviceOfflineLog> records = new Page<>(1, 50);
        when(offlineLogService.page(
                ArgumentMatchers.<Page<DeviceOfflineLog>>any(),
                any())).thenReturn(records);
        String token = JwtUtil.generateToken(7L, "user-7", "app");

        Result<Object> result = controller.offlineRecords(12L, 0, 500, "Bearer " + token);

        assertEquals(200, result.getCode());
        assertEquals(records, result.getData());
        verify(offlineLogService).page(
                ArgumentMatchers.argThat(page -> page.getCurrent() == 1 && page.getSize() == 50),
                any());
    }
}
