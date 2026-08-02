package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerVirtualDeviceDetailTest {

    @Mock
    private IDeviceService deviceService;

    @InjectMocks
    private DeviceController controller;

    @Test
    void fillsVirtualDeviceTelemetryEnvironmentAndNodeNumber() {
        Device virtualDevice = ownedDevice(42L, 7L, 1);
        when(deviceService.getById(42L)).thenReturn(virtualDevice);

        Result<Device> result = controller.detail(42L, userToken(7L));

        assertEquals(200, result.getCode());
        Device detail = result.getData();
        assertEquals(1, detail.getStatus());
        assertPercentBetween(detail.getCpuUsage(), 18, 62);
        assertPercentBetween(detail.getMemoryUsage(), 38, 74);
        assertPercentBetween(detail.getGpuUsage(), 12, 68);
        assertEquals("NVIDIA Jetson AGX Orin", detail.getDeviceModel());
        assertEquals("ARM Cortex-A78AE", detail.getCpuModel());
        assertEquals("aarch64", detail.getArchitecture());
        assertEquals("36.4.7", detail.getL4tVersion());
        assertEquals("12.6", detail.getCudaVersion());
        assertEquals("1.0.0", detail.getAgentVersion());
        assertEquals("orin-l4t-36.4.7-v1", detail.getImageVersion());
        assertEquals("YW8000000000042", detail.getBusinessId());
        assertNotNull(detail.getLastHeartbeatTime());
    }

    @Test
    void keepsExistingVirtualDeviceValues() {
        Device virtualDevice = ownedDevice(42L, 7L, 1);
        virtualDevice.setCpuUsage("91");
        virtualDevice.setBusinessId("YW-CUSTOM");
        virtualDevice.setCudaVersion("custom-cuda");
        when(deviceService.getById(42L)).thenReturn(virtualDevice);

        Device detail = controller.detail(42L, userToken(7L)).getData();

        assertEquals("91", detail.getCpuUsage());
        assertEquals("YW-CUSTOM", detail.getBusinessId());
        assertEquals("custom-cuda", detail.getCudaVersion());
    }

    @Test
    void doesNotChangeRealDeviceTelemetry() {
        Device realDevice = ownedDevice(42L, 7L, 2);
        LocalDateTime heartbeat = LocalDateTime.of(2026, 8, 2, 10, 30);
        realDevice.setStatus(0);
        realDevice.setLastHeartbeatTime(heartbeat);
        when(deviceService.getById(42L)).thenReturn(realDevice);

        Device detail = controller.detail(42L, userToken(7L)).getData();

        assertEquals(0, detail.getStatus());
        assertEquals(heartbeat, detail.getLastHeartbeatTime());
        assertNull(detail.getCpuUsage());
        assertNull(detail.getBusinessId());
        assertNull(detail.getArchitecture());
    }

    @Test
    void producesStableValuesInsideTheSameTelemetryWindow() {
        Device first = ownedDevice(99L, 7L, 1);
        Device second = ownedDevice(99L, 7L, 1);
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 30, 5);

        DeviceController.enrichVirtualDeviceDetail(first, now);
        DeviceController.enrichVirtualDeviceDetail(second, now.plusSeconds(4));

        assertEquals(first.getCpuUsage(), second.getCpuUsage());
        assertEquals(first.getMemoryUsage(), second.getMemoryUsage());
        assertEquals(first.getGpuUsage(), second.getGpuUsage());
        assertEquals(first.getBusinessId(), second.getBusinessId());
    }

    private Device ownedDevice(Long id, Long userId, int type) {
        Device device = new Device();
        device.setId(id);
        device.setUserId(userId);
        device.setType(type);
        return device;
    }

    private String userToken(Long userId) {
        return "Bearer " + JwtUtil.generateToken(userId, "user-" + userId, "app");
    }

    private void assertPercentBetween(String value, int min, int max) {
        assertNotNull(value);
        int percent = Integer.parseInt(value);
        assertTrue(percent >= min && percent <= max);
    }
}
