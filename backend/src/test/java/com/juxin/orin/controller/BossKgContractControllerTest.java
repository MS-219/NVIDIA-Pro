package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.UserContract;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.mapper.UserContractMapper;
import com.juxin.orin.mapper.UserPaymentApplyMapper;
import com.juxin.orin.service.IBossKgService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BossKgContractControllerTest {

    @Mock
    private IBossKgService bossKgService;

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private UserContractMapper userContractMapper;

    @Mock
    private UserPaymentApplyMapper applyMapper;

    @InjectMocks
    private BossKgContractController controller;

    @Test
    void userWithoutContractIsReturnedAsPendingWithoutRemoteSync() {
        when(bossKgService.isEnabled()).thenReturn(true);
        when(bossKgService.getUserContract(42L)).thenReturn(null);

        MockHttpServletRequest request = authenticatedRequest(42L);
        Result<Map<String, Object>> response = controller.getContractStatus(request);

        assertEquals(200, response.getCode());
        assertEquals(false, response.getData().get("contracted"));
        assertEquals(UserContract.STATUS_PENDING, response.getData().get("status"));
        verify(bossKgService, never()).queryContractStatus(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private MockHttpServletRequest authenticatedRequest(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + JwtUtil.generateToken(userId, "user", "app"));
        return request;
    }
}
