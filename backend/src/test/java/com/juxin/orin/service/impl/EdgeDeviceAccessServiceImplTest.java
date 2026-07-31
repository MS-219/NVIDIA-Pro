package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.juxin.orin.dto.EdgeEnrollRequest;
import com.juxin.orin.dto.EdgeEnrollResponse;
import com.juxin.orin.entity.Device;
import com.juxin.orin.exception.EdgeDeviceApiException;
import com.juxin.orin.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeDeviceAccessServiceImplTest {

    private static final String TEST_SECRET = "test-enrollment-secret-with-at-least-32-bytes";

    @Mock
    private DeviceMapper deviceMapper;

    private EdgeDeviceAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EdgeDeviceAccessServiceImpl(
                deviceMapper,
                TEST_SECRET);
    }

    @Test
    void enrollCreatesDeviceAndStoresOnlyTokenHash() {
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, null, null);
        doAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            device.setId(42L);
            return 1;
        }).when(deviceMapper).insert(any(Device.class));

        EdgeEnrollResponse response = service.enroll(new EdgeEnrollRequest(
                "ORIN-001",
                "orin-l4t-36.4.7-v1",
                "0123456789abcdef",
                Map.of(
                        "device_model", "Jetson Orin Nano Super",
                        "architecture", "aarch64",
                        "agent_version", "1.0.0",
                        "gpu_temperature", 42.5)),
                "203.0.113.10");

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).insert(deviceCaptor.capture());
        Device stored = deviceCaptor.getValue();

        assertEquals("ORIN-001", response.deviceSn());
        assertEquals(42L, response.deviceId());
        assertTrue(response.bindCode().matches("Orin-[A-F0-9]{6}"));
        assertEquals(response.bindCode(), stored.getBindCode());
        assertNotNull(response.deviceToken());
        assertEquals(43, response.deviceToken().length());
        assertEquals(64, stored.getDeviceTokenHash().length());
        assertEquals(EdgeDeviceAccessServiceImpl.sha256(response.deviceToken()), stored.getDeviceTokenHash());
        assertNotEquals(response.deviceToken(), stored.getDeviceTokenHash());
        assertFalse(stored.getDeviceTokenHash().contains(response.deviceToken()));
        assertEquals("0123456789abcdef", stored.getHardwareFingerprint());
        assertEquals("aarch64", stored.getArchitecture());
        assertEquals(42.5, stored.getGpuTemperature());
    }

    @Test
    void enrollRetriesWhenFirstShortCodeIsOwnedByAnotherDevice() {
        String sn = "ORIN-0123456789ABCDEF";
        String firstCandidate = "Orin-" + EdgeDeviceAccessServiceImpl.sha256(sn)
                .substring(0, 6).toUpperCase();
        Device owner = new Device();
        owner.setSn("ORIN-OTHER");
        owner.setBindCode(firstCandidate);
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, null, owner, null);
        doAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            device.setId(43L);
            return 1;
        }).when(deviceMapper).insert(any(Device.class));

        EdgeEnrollResponse response = service.enroll(
                enrollmentRequest(sn), "203.0.113.20");

        assertTrue(response.bindCode().matches("Orin-[A-F0-9]{6}"));
        assertNotEquals(firstCandidate, response.bindCode());
    }

    @Test
    void enrollNeverReissuesTokenForAlreadyEnrolledDevice() {
        Device existing = new Device();
        existing.setId(8L);
        existing.setSn("ORIN-001");
        existing.setHardwareFingerprint("0123456789abcdef");
        existing.setDeviceTokenHash("a".repeat(64));

        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing, existing);

        EdgeDeviceApiException exception = assertThrows(EdgeDeviceApiException.class, () -> service.enroll(
                new EdgeEnrollRequest(
                        "ORIN-001",
                        "orin-l4t-36.4.7-v1",
                        "0123456789abcdef",
                        Map.of()),
                "203.0.113.10"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(deviceMapper, never()).updateById(any(Device.class));
    }

    @Test
    void enrollmentRetryRecoversSameTokenAfterResponseLoss() {
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, null, null);
        doAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            device.setId(42L);
            return 1;
        }).when(deviceMapper).insert(any(Device.class));

        EdgeEnrollRequest request = enrollmentRequest("ORIN-RETRY");
        EdgeEnrollResponse first = service.enroll(request, "203.0.113.12");

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).insert(deviceCaptor.capture());
        Device stored = deviceCaptor.getValue();
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored, stored);

        EdgeEnrollResponse retry = service.enroll(request, "203.0.113.12");

        assertEquals(first.deviceToken(), retry.deviceToken());
        assertEquals(first.deviceSn(), retry.deviceSn());
        verify(deviceMapper).updateById(stored);
    }

    @Test
    void concurrentEnrollmentReturnsWinningTokenWhenConditionalClaimLoses() {
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, null, null);
        doAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            device.setId(77L);
            return 1;
        }).when(deviceMapper).insert(any(Device.class));

        EdgeEnrollRequest request = enrollmentRequest("ORIN-RACE");
        EdgeEnrollResponse winnerResponse = service.enroll(request, "203.0.113.13");
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).insert(deviceCaptor.capture());
        Device winner = deviceCaptor.getValue();

        Device stale = new Device();
        stale.setId(winner.getId());
        stale.setSn(winner.getSn());
        stale.setBindCode(winner.getBindCode());
        stale.setHardwareFingerprint(winner.getHardwareFingerprint());
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stale, stale, winner);
        when(deviceMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        EdgeEnrollResponse loserResponse = service.enroll(request, "203.0.113.13");

        assertEquals(winnerResponse.deviceToken(), loserResponse.deviceToken());
        verify(deviceMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void enrollRejectsFingerprintBoundToAnotherDevice() {
        Device owner = new Device();
        owner.setSn("ORIN-OTHER");
        owner.setHardwareFingerprint("fedcba9876543210");
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(owner);

        EdgeDeviceApiException exception = assertThrows(EdgeDeviceApiException.class, () -> service.enroll(
                new EdgeEnrollRequest(
                        "ORIN-002",
                        "orin-l4t-36.4.7-v1",
                        "fedcba9876543210",
                        Map.of()),
                "203.0.113.11"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(deviceMapper, never()).insert(any(Device.class));
    }

    @Test
    void authenticateHashesPresentedTokenBeforeLookup() {
        Device device = new Device();
        device.setSn("ORIN-001");
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(device);

        assertEquals(device, service.authenticate("A".repeat(43)));
        verify(deviceMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void missingTokenIsUnauthorizedWithoutDatabaseLookup() {
        EdgeDeviceApiException exception = assertThrows(
                EdgeDeviceApiException.class,
                () -> service.authenticate(" "));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(deviceMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    private EdgeEnrollRequest enrollmentRequest(String sn) {
        return new EdgeEnrollRequest(
                sn,
                "orin-l4t-36.4.7-v1",
                "0123456789abcdef",
                Map.of("architecture", "aarch64", "agent_version", "1.0.0"));
    }
}
