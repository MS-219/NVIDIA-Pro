package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.service.ISystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WithdrawControllerAllowedDaysTest {

    @Test
    void applyExplainsTheConfiguredAllowedDays() {
        WithdrawController controller = new WithdrawController();
        ISystemConfigService configService = mock(ISystemConfigService.class);
        int today = java.time.LocalDate.now().getDayOfWeek().getValue();
        int otherDay = today == 7 ? 1 : today + 1;
        when(configService.getConfig("withdraw.allowedDays", "")).thenReturn(String.valueOf(otherDay));
        ReflectionTestUtils.setField(controller, "configService", configService);

        Result<String> response = controller.apply(Map.of());

        assertTrue(response.getMsg().contains("允许提现日"));
    }
}
