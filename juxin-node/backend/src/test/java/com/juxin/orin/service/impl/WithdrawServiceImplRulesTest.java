package com.juxin.orin.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WithdrawServiceImplRulesTest {

    @Test
    void withdrawalOnlyRequiresTheMinimumCurrencyUnit() {
        WithdrawServiceImpl service = new WithdrawServiceImpl();

        String error = service.applyWithdraw(1L, new BigDecimal("0.009"), 3, "6222020000000000", "测试用户", null);

        assertEquals("提现金额最低为 0.01 元", error);
    }
}
