package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.entity.Withdraw;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IBossKgService;
import com.juxin.orin.service.IWithdrawService;
import com.juxin.orin.util.JwtUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawControllerFailedHistoryTest {

    @BeforeAll
    static void initWithdrawMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Withdraw.class);
    }

    @Test
    void unfilteredAdminListDoesNotHideFailedWithdrawalsByAge() {
        IWithdrawService withdrawService = mock(IWithdrawService.class);
        when(withdrawService.page(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>(1, 10));

        WithdrawController controller = controller(withdrawService);
        controller.adminList(1, 10, null, null, null, null,
                "Bearer " + JwtUtil.generateToken(1L, "admin", "admin"));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Wrapper<Withdraw>> query =
                (org.mockito.ArgumentCaptor<Wrapper<Withdraw>>) (Object) org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(withdrawService).page(any(Page.class), query.capture());
        assertFalse(query.getValue().getSqlSegment().contains("update_time"));
    }

    @Test
    void explicitFailedStatusFilterDoesNotApplyAnAgeLimit() {
        IWithdrawService withdrawService = mock(IWithdrawService.class);
        when(withdrawService.page(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>(1, 10));

        WithdrawController controller = controller(withdrawService);
        controller.adminList(1, 10, 4, null, null, null,
                "Bearer " + JwtUtil.generateToken(1L, "admin", "admin"));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Wrapper<Withdraw>> query =
                (org.mockito.ArgumentCaptor<Wrapper<Withdraw>>) (Object) org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(withdrawService).page(any(Page.class), query.capture());
        assertFalse(query.getValue().getSqlSegment().contains("update_time"));
    }

    private WithdrawController controller(IWithdrawService withdrawService) {
        WithdrawController controller = new WithdrawController();
        ReflectionTestUtils.setField(controller, "withdrawService", withdrawService);
        ReflectionTestUtils.setField(controller, "appUserService", mock(IAppUserService.class));
        ReflectionTestUtils.setField(controller, "bossKgService", mock(IBossKgService.class));
        return controller;
    }
}
