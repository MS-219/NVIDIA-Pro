package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AppUser;
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

    @Test
    void pendingContractIsRefreshedFromBossKg() {
        UserContract pending = contract(UserContract.STATUS_PENDING);
        UserContract signed = contract(UserContract.STATUS_SUCCESS);
        when(bossKgService.isEnabled()).thenReturn(true);
        when(bossKgService.getUserContract(42L)).thenReturn(pending, signed);

        Result<Map<String, Object>> response = controller.getContractStatus(authenticatedRequest(42L));

        assertEquals(200, response.getCode());
        assertEquals(true, response.getData().get("contracted"));
        assertEquals(UserContract.STATUS_SUCCESS, response.getData().get("status"));
        verify(bossKgService).queryContractStatus("张三", "110101199001011234", "13800138000");
    }

    @Test
    void refreshImportsExistingBossKgContractWithoutLocalRecord() {
        AppUser user = new AppUser();
        user.setId(42L);
        user.setBankHolderName("张三");
        user.setIdCard("110101199001011234");
        user.setPhone("13800138000");
        when(bossKgService.isEnabled()).thenReturn(true);
        when(bossKgService.getUserContract(42L)).thenReturn(null);
        when(appUserMapper.selectById(42L)).thenReturn(user);
        when(bossKgService.queryContractStatus("张三", "110101199001011234", "13800138000"))
                .thenReturn(Map.of("success", true, "state", 1));

        Result<Map<String, Object>> response = controller.refreshContractStatus(authenticatedRequest(42L));

        assertEquals(200, response.getCode());
        assertEquals(true, response.getData().get("success"));
        assertEquals(1, response.getData().get("state"));
    }

    private UserContract contract(int status) {
        UserContract contract = new UserContract();
        contract.setUserId(42L);
        contract.setStatus(status);
        contract.setRealName("张三");
        contract.setIdCard("110101199001011234");
        contract.setMobile("13800138000");
        return contract;
    }

    private MockHttpServletRequest authenticatedRequest(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + JwtUtil.generateToken(userId, "user", "app"));
        return request;
    }
}
