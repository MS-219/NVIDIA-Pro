package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppUserControllerAuthorizationTest {

    private final AppUserController controller = new AppUserController();

    @Test
    void updateLevelRejectsMissingAdminTokenBeforeMutation() {
        Result<String> result = controller.updateLevel(new HashMap<>(), null);

        assertEquals(500, result.getCode());
        assertEquals("未登录，请先登录", result.getMsg());
    }

    @Test
    void updateInviterRejectsMissingAdminTokenBeforeMutation() {
        Result<String> result = controller.updateInviter(new HashMap<>(), null);

        assertEquals(500, result.getCode());
        assertEquals("未登录，请先登录", result.getMsg());
    }

    @Test
    void rechargeQuotaRejectsMissingAdminTokenBeforeMutation() {
        Result<String> result = controller.rechargeQuota(Map.of(), null);

        assertEquals(500, result.getCode());
        assertEquals("未登录，请先登录", result.getMsg());
    }

    @Test
    void updateEarningsRangeRejectsMissingAdminTokenBeforeMutation() {
        Result<String> result = controller.updateEarningsRange(Map.of(), null);

        assertEquals(500, result.getCode());
        assertEquals("未登录，请先登录", result.getMsg());
    }
}
