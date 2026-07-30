package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.SystemConfig;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.ISystemConfigService;
import com.juxin.orin.service.InviteLevelConfigService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsControllerInviteLevelsTest {

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
    void savesAdditionalLevelsAndPersistsTheDynamicCount() {
        when(configService.getConfig(InviteLevelConfigService.LEVEL_COUNT_KEY, "5")).thenReturn("5");

        Result<Object> result = controller.saveEarningsSettings(
                Map.of("inviteLevels", levels(6)), adminToken());

        assertEquals(200, result.getCode());
        verify(configService).setConfig("invite.level6.name", "L6");
        verify(configService).setConfig("invite.level6.threshold", "60");
        verify(configService).setConfig("invite.level6.rate", "0.6");
        verify(configService).setConfig(InviteLevelConfigService.LEVEL_COUNT_KEY, "6");
        verify(appUserService).clampUserLevels(6);
        verify(appUserService).updateAllUserLevels();
    }

    @Test
    void deletesObsoleteLevelKeysWhenTheListShrinks() {
        when(configService.getConfig(InviteLevelConfigService.LEVEL_COUNT_KEY, "5")).thenReturn("5");

        Result<Object> result = controller.saveEarningsSettings(
                Map.of("inviteLevels", levels(2)), adminToken());

        assertEquals(200, result.getCode());
        verify(configService).remove(any(Wrapper.class));
        verify(configService).setConfig(InviteLevelConfigService.LEVEL_COUNT_KEY, "2");
        verify(appUserService).clampUserLevels(2);
        verify(appUserService).updateAllUserLevels();
    }

    @Test
    void rejectsNonIncreasingThresholdsBeforeSavingAnything() {
        List<Map<String, Object>> levels = levels(2);
        levels.get(1).put("threshold", 10);

        Result<Object> result = controller.saveEarningsSettings(
                Map.of("inviteLevels", levels), adminToken());

        assertEquals(500, result.getCode());
        assertEquals("等级门槛必须按等级严格递增", result.getMsg());
        verify(configService, never()).setConfig(any(), any());
        verify(appUserService, never()).updateAllUserLevels();
    }

    private List<Map<String, Object>> levels(int count) {
        List<Map<String, Object>> levels = new ArrayList<>();
        for (int level = 1; level <= count; level++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", 100 + level);
            item.put("name", "L" + level);
            item.put("threshold", level * 10);
            item.put("rate", level / 10.0);
            levels.add(item);
        }
        return levels;
    }

    private String adminToken() {
        return "Bearer " + JwtUtil.generateToken(1L, "admin", "admin");
    }
}
