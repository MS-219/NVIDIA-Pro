package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.exception.EdgeDeviceApiException;
import com.juxin.orin.service.IComputeJobService;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IEdgeDeviceAccessService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IDeviceUpgradeService;
import com.juxin.orin.service.ISystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeDeviceControllerAuthorizationTest {

    @Mock
    private IDeviceService deviceService;

    @Mock
    private IEdgeDeviceAccessService edgeDeviceAccessService;

    @Mock
    private IComputeJobService computeJobService;

    @Mock
    private IDeviceCommandService deviceCommandService;

    @Mock
    private IDeviceUpgradeService deviceUpgradeService;

    @Mock
    private ISystemConfigService configService;

    @InjectMocks
    private EdgeDeviceController controller;

    @Test
    void capabilitiesExposeTheManufacturingProtocolGate() {
        Map<String, Object> capabilities = controller.capabilities().getData();

        assertEquals("2", capabilities.get("protocolVersion"));
        assertEquals("0.5.0-orin", capabilities.get("minimumAgentVersion"));
        assertEquals(true, capabilities.get("directEnrollment"));
        assertEquals(false, capabilities.get("imageLicenseRequired"));
        assertEquals(true, capabilities.get("deviceTokenAuthentication"));
        assertEquals(true, capabilities.get("fullscreenStatusDisplay"));
        assertEquals(true, capabilities.get("atomicTaskClaim"));
        assertEquals(true, capabilities.get("persistentResultOutbox"));
    }

    @Test
    void reportRejectsSnThatDoesNotBelongToToken() {
        Device authenticated = device("ORIN-001");
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        doThrow(new EdgeDeviceApiException(HttpStatus.FORBIDDEN, "mismatch"))
                .when(edgeDeviceAccessService).requireOwnedSn(authenticated, "ORIN-002");

        EdgeDeviceApiException exception = assertThrows(EdgeDeviceApiException.class, () -> controller.reportStatus(
                Map.of("sn", "ORIN-002"),
                new org.springframework.mock.web.MockHttpServletRequest(),
                "token-a"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(deviceService, never()).handleHeartbeat(
                any(), any(), any(), any());
    }

    @Test
    void reportReturnsBackendManagedRuntimeIntervals() {
        Device authenticated = device("ORIN-001");
        authenticated.setType(2);
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        when(deviceService.handleHeartbeat("ORIN-001", "127.0.0.1", "0", "0"))
                .thenReturn(authenticated);
        when(configService.getConfig("device.heartbeatInterval", "60")).thenReturn("90");
        when(configService.getConfig("device.taskPollInterval", "60")).thenReturn("15");
        when(configService.getConfig("device.offlineThreshold", "180")).thenReturn("240");

        Map<String, Object> response = controller.reportStatus(
                Map.of("sn", "ORIN-001"),
                new org.springframework.mock.web.MockHttpServletRequest(),
                "token-a").getData();

        assertEquals(90, response.get("heartbeatInterval"));
        assertEquals(15, response.get("taskPollInterval"));
        assertEquals(240, response.get("offlineThreshold"));
    }

    @Test
    void reportFallsBackWhenStoredRuntimeConfigIsInvalid() {
        Device authenticated = device("ORIN-001");
        authenticated.setType(2);
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        when(deviceService.handleHeartbeat("ORIN-001", "127.0.0.1", "0", "0"))
                .thenReturn(authenticated);
        when(configService.getConfig("device.heartbeatInterval", "60")).thenReturn("invalid");
        when(configService.getConfig("device.taskPollInterval", "60")).thenReturn("9999");
        when(configService.getConfig("device.offlineThreshold", "180")).thenReturn("1");

        Map<String, Object> response = controller.reportStatus(
                Map.of("sn", "ORIN-001"),
                new org.springframework.mock.web.MockHttpServletRequest(),
                "token-a").getData();

        assertEquals(60, response.get("heartbeatInterval"));
        assertEquals(300, response.get("taskPollInterval"));
        assertEquals(30, response.get("offlineThreshold"));
    }

    @Test
    void taskResultCannotBeSubmittedForAnotherDevice() {
        Device authenticated = device("ORIN-001");
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        doNothing().when(edgeDeviceAccessService).requireOwnedSn(authenticated, "ORIN-001");

        ComputeJob submitted = new ComputeJob();
        submitted.setId(9L);
        submitted.setDeviceSn("ORIN-001");
        submitted.setStatus("completed");

        ComputeJob stored = new ComputeJob();
        stored.setId(9L);
        stored.setDeviceSn("ORIN-OTHER");
        stored.setStatus("running");
        when(computeJobService.getById(9L)).thenReturn(stored);

        EdgeDeviceApiException exception = assertThrows(
                EdgeDeviceApiException.class,
                () -> controller.submitResult(submitted, "token-a"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(computeJobService, never()).updateById(any(ComputeJob.class));
    }

    @Test
    void commandResultCannotBeSubmittedForAnotherDevice() {
        Device authenticated = device("ORIN-001");
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        doNothing().when(edgeDeviceAccessService).requireOwnedSn(authenticated, "ORIN-001");

        DeviceCommand command = new DeviceCommand();
        command.setCommandNo("CMD-1");
        command.setDeviceSn("ORIN-OTHER");
        when(deviceCommandService.getOne(any(LambdaQueryWrapper.class))).thenReturn(command);

        EdgeDeviceApiException exception = assertThrows(EdgeDeviceApiException.class, () ->
                controller.submitCommandResult(
                        Map.of("sn", "ORIN-001", "commandNo", "CMD-1", "exitCode", 0),
                        "token-a"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(deviceCommandService, never()).submitResult(any(), any(), any());
    }

    @Test
    void taskFetchUsesAuthenticatedDeviceSn() {
        Device authenticated = device("ORIN-001");
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        doNothing().when(edgeDeviceAccessService).requireOwnedSn(authenticated, "ORIN-001");
        when(computeJobService.claimNextPendingTask("ORIN-001")).thenReturn(null);

        assertEquals(200, controller.fetchTask("ORIN-001", "token-a").getCode());

        verify(edgeDeviceAccessService).requireOwnedSn(authenticated, "ORIN-001");
        verify(computeJobService).claimNextPendingTask("ORIN-001");
    }

    @Test
    void duplicateTaskResultIsIdempotent() {
        Device authenticated = device("ORIN-001");
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        doNothing().when(edgeDeviceAccessService).requireOwnedSn(authenticated, "ORIN-001");

        ComputeJob submitted = new ComputeJob();
        submitted.setId(9L);
        submitted.setDeviceSn("ORIN-001");
        submitted.setStatus("completed");

        ComputeJob stored = new ComputeJob();
        stored.setId(9L);
        stored.setDeviceSn("ORIN-001");
        stored.setStatus("completed");
        when(computeJobService.getById(9L)).thenReturn(stored);

        assertEquals(200, controller.submitResult(submitted, "token-a").getCode());

        verify(computeJobService, never()).update(any(UpdateWrapper.class));
    }

    @Test
    void taskResultUsesConditionalOwnershipUpdate() {
        Device authenticated = device("ORIN-001");
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        doNothing().when(edgeDeviceAccessService).requireOwnedSn(authenticated, "ORIN-001");

        ComputeJob submitted = new ComputeJob();
        submitted.setId(10L);
        submitted.setDeviceSn("ORIN-001");
        submitted.setStatus("completed");
        submitted.setGenerateTokens(230);

        ComputeJob stored = new ComputeJob();
        stored.setId(10L);
        stored.setDeviceSn("ORIN-001");
        stored.setStatus("running");
        when(computeJobService.getById(10L)).thenReturn(stored);
        when(computeJobService.update(any(UpdateWrapper.class))).thenReturn(true);

        assertEquals(200, controller.submitResult(submitted, "token-a").getCode());

        verify(computeJobService).update(any(UpdateWrapper.class));
        verify(computeJobService, never()).updateById(any(ComputeJob.class));
    }

    @Test
    void taskResultRejectsAConcurrentOwnershipChange() {
        Device authenticated = device("ORIN-001");
        when(edgeDeviceAccessService.authenticate("token-a")).thenReturn(authenticated);
        doNothing().when(edgeDeviceAccessService).requireOwnedSn(authenticated, "ORIN-001");

        ComputeJob submitted = new ComputeJob();
        submitted.setId(12L);
        submitted.setDeviceSn("ORIN-001");
        submitted.setStatus("failed");

        ComputeJob running = new ComputeJob();
        running.setId(12L);
        running.setDeviceSn("ORIN-001");
        running.setStatus("running");
        ComputeJob reassigned = new ComputeJob();
        reassigned.setId(12L);
        reassigned.setDeviceSn("ORIN-002");
        reassigned.setStatus("running");
        when(computeJobService.getById(12L)).thenReturn(running, reassigned);
        when(computeJobService.update(any(UpdateWrapper.class))).thenReturn(false);

        EdgeDeviceApiException exception = assertThrows(
                EdgeDeviceApiException.class,
                () -> controller.submitResult(submitted, "token-a"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    private Device device(String sn) {
        Device device = new Device();
        device.setId(1L);
        device.setSn(sn);
        return device;
    }
}
