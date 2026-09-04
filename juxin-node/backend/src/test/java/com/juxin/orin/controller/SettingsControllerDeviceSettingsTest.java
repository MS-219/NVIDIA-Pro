package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.ISystemConfigService;
import com.juxin.orin.service.InviteLevelConfigService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsControllerDeviceSettingsTest {

    @Mock
    private ISystemConfigService configService;

    @Mock
    private IAppUserService appUserService;

    private SettingsController controller;

    @BeforeEach
    void setUp() {
        controller = new SettingsController();
        ReflectionTestUtils.setField(controller, "configService", configService);
        ReflectionTestUtils.setField(controller, "inviteLevelConfigService",
                new InviteLevelConfigService(configService));
        ReflectionTestUtils.setField(controller, "appUserService", appUserService);
    }

    @Test
    void deviceSettingsExposeMaxnSuperByDefault() {
        when(configService.getConfig(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        Map<?, ?> settings = (Map<?, ?>) controller.getAllSettings(adminToken()).getData();
        Map<?, ?> device = (Map<?, ?>) settings.get("device");

        assertEquals("MAXN_SUPER", device.get("powerMode"));
    }

    @Test
    void savesSupportedGlobalPowerMode() {
        Result<Object> result = controller.saveDeviceSettings(
                Map.of("powerMode", "25W"), adminToken());

        assertEquals(200, result.getCode());
        verify(configService).setConfig("device.powerMode", "25W");
    }

    @Test
    void rejectsUnsupportedGlobalPowerModeWithoutPersisting() {
        Result<Object> result = controller.saveDeviceSettings(
                Map.of("powerMode", "mode-2"), adminToken());

        assertEquals(500, result.getCode());
        assertEquals("功耗模式仅支持 15W、25W 或 MAXN_SUPER", result.getMsg());
        verify(configService, never()).setConfig(any(), any());
    }

    private String adminToken() {
        return "Bearer " + JwtUtil.generateToken(1L, "admin", "admin");
    }
}
