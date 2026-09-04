package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.service.ISystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsControllerWithdrawRulesTest {

    @Test
    void withdrawalStatusExplainsConfiguredAllowedDays() {
        SettingsController controller = new SettingsController();
        ISystemConfigService configService = mock(ISystemConfigService.class);
        int today = java.time.LocalDate.now().getDayOfWeek().getValue();
        int otherDay = today == 7 ? 1 : today + 1;
        when(configService.getConfig("withdraw.allowedDays", "")).thenReturn(String.valueOf(otherDay));
        ReflectionTestUtils.setField(controller, "configService", configService);

        Result<Object> response = controller.getWithdrawStatus();

        assertEquals(200, response.getCode());
        Map<?, ?> rules = (Map<?, ?>) response.getData();
        assertEquals(Boolean.FALSE, rules.get("canWithdraw"));
        assertEquals(String.valueOf(otherDay), rules.get("allowedDays"));
        assertEquals(0.01D, rules.get("minWithdraw"));
        assertTrue(rules.get("message").toString().contains("允许提现日"));
        assertTrue(rules.get("allowedDaysText").toString().startsWith("周"));
    }
}
