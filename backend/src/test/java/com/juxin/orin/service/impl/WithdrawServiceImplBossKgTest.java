package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.UserContract;
import com.juxin.orin.entity.Withdraw;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.mapper.WithdrawMapper;
import com.juxin.orin.service.IBossKgService;
import com.juxin.orin.service.ISystemConfigService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceImplBossKgTest {

    private static final Long WITHDRAW_ID = 7L;
    private static final Long USER_ID = 3L;

    @Mock
    private WithdrawMapper withdrawMapper;

    @Mock
    private IBossKgService bossKgService;

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private ISystemConfigService configService;

    private WithdrawServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Withdraw.class);
    }

    @BeforeEach
    void setUp() {
        service = new WithdrawServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", withdrawMapper);
        ReflectionTestUtils.setField(service, "withdrawMapper", withdrawMapper);
        ReflectionTestUtils.setField(service, "bossKgService", bossKgService);
        ReflectionTestUtils.setField(service, "appUserMapper", appUserMapper);
        ReflectionTestUtils.setField(service, "configService", configService);
    }

    @Test
    void retryPaymentSubmitsReloadedRecordInsteadOfStaleOrderNo() {
        Withdraw stale = withdraw(4, 4);
        stale.setBossKgBatchId("OLD-BATCH");
        stale.setBossKgOrderNo("OLD-ORDER");
        // 预占后库里的 batchId/orderNo 已被清空
        Withdraw reserved = withdraw(1, 1);
        Withdraw afterReject = withdraw(1, 1);
        when(withdrawMapper.selectById(WITHDRAW_ID)).thenReturn(stale, reserved, afterReject);
        when(withdrawMapper.update(ArgumentMatchers.<Withdraw>any(), ArgumentMatchers.<Wrapper<Withdraw>>any()))
                .thenReturn(1);
        when(bossKgService.isEnabled()).thenReturn(true);
        when(bossKgService.getUserContract(USER_ID)).thenReturn(signedContract());
        when(appUserMapper.selectById(USER_ID)).thenReturn(new AppUser());
        lenient().when(configService.getConfig(anyString(), anyString())).thenReturn("1");

        AtomicReference<String> submittedOrderNo = new AtomicReference<>("not-called");
        AtomicReference<String> submittedBatchId = new AtomicReference<>("not-called");
        when(bossKgService.payment(ArgumentMatchers.any(Withdraw.class), eq("110101199001011234")))
                .thenAnswer(invocation -> {
                    Withdraw submitted = invocation.getArgument(0);
                    submittedOrderNo.set(submitted.getBossKgOrderNo());
                    submittedBatchId.set(submitted.getBossKgBatchId());
                    return "商户余额不足,请联系管理员充值";
                });

        String result = service.payViaBossKg(WITHDRAW_ID, 99L);

        assertEquals("商户余额不足,请联系管理员充值", result);
        assertNull(submittedOrderNo.get(), "不能把上一次失败的平台订单号带进新一轮付款");
        assertNull(submittedBatchId.get(), "不能复用上一次失败的批次号");
    }

    @Test
    void failedPaymentSyncClearsBatchIdInDatabase() {
        Withdraw paying = withdraw(1, 1);
        paying.setBossKgBatchId("B1");
        paying.setBossKgOrderNo("O1");
        when(withdrawMapper.selectById(WITHDRAW_ID)).thenReturn(paying);
        when(withdrawMapper.updateById(ArgumentMatchers.any(Withdraw.class))).thenReturn(1);
        Map<String, Object> queryResult = new HashMap<>();
        queryResult.put("success", true);
        queryResult.put("state", 4);
        queryResult.put("resMsg", "收款账户异常");
        when(bossKgService.queryPaymentStatus("B1", "O1")).thenReturn(queryResult);

        assertTrue(service.syncBossKgStatus(WITHDRAW_ID));

        ArgumentCaptor<Withdraw> updated = ArgumentCaptor.forClass(Withdraw.class);
        verify(withdrawMapper).updateById(updated.capture());
        assertEquals(4, updated.getValue().getStatus());
        assertEquals(4, updated.getValue().getBossKgState());
        assertBatchIdClearedExplicitly(withdrawMapper);
    }

    @SuppressWarnings("unchecked")
    static void assertBatchIdClearedExplicitly(WithdrawMapper mapper) {
        ArgumentCaptor<Wrapper<Withdraw>> wrappers = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper, atLeastOnce()).update(isNull(), wrappers.capture());
        List<Wrapper<Withdraw>> captured = wrappers.getAllValues();
        boolean cleared = captured.stream()
                .map(w -> (AbstractWrapper<Withdraw, ?, ?>) w)
                .anyMatch(w -> w.getSqlSet() != null
                        && w.getSqlSet().contains("boss_kg_batch_id=")
                        && w.getParamNameValuePairs().containsValue(null));
        assertTrue(cleared, "batchId 必须通过显式 set null 的 UpdateWrapper 清空，updateById 会跳过 null 字段");
    }

    private static Withdraw withdraw(int status, int bossKgState) {
        Withdraw withdraw = new Withdraw();
        withdraw.setId(WITHDRAW_ID);
        withdraw.setUserId(USER_ID);
        withdraw.setStatus(status);
        withdraw.setBossKgState(bossKgState);
        withdraw.setAmount(new BigDecimal("100.00"));
        withdraw.setActualAmount(new BigDecimal("99.00"));
        withdraw.setPaymentFailCount(0);
        return withdraw;
    }

    private static UserContract signedContract() {
        UserContract contract = new UserContract();
        contract.setUserId(USER_ID);
        contract.setStatus(UserContract.STATUS_SUCCESS);
        contract.setRealName("测试用户");
        contract.setIdCard("110101199001011234");
        contract.setMobile("13800000000");
        contract.setBankCardNo("6222020000000000");
        assertNotNull(contract.getIdCard());
        return contract;
    }
}
