package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.Withdraw;

import java.math.BigDecimal;
import java.util.Map;

public interface IWithdrawService extends IService<Withdraw> {

    /**
     * 申请提现
     */
    String applyWithdraw(Long userId, BigDecimal amount, Integer type, String account, String realName, String qrCode);

    /**
     * 获取用户钱包信息（可提现余额、待审核金额等）
     */
    Map<String, BigDecimal> getWalletInfo(Long userId);

    /**
     * 获取用户提现记录
     */
    Page<Withdraw> getUserWithdrawList(Long userId, Integer page, Integer size);

    /**
     * 审核提现（通过）
     */
    boolean approve(Long id, Long auditorId);

    /**
     * 审核提现（拒绝）
     */
    boolean reject(Long id, Long auditorId, String remark);

    /**
     * 确认打款
     */
    boolean confirmPaid(Long id, Long auditorId);

    /**
     * 通过佣金保发起付款
     * 
     * @param id        提现记录ID
     * @param auditorId 操作人ID
     * @return 成功返回null, 失败返回错误信息
     */
    String payViaBossKg(Long id, Long auditorId);

    /**
     * 同步佣金保付款状态
     * 
     * @param id 提现记录ID
     * @return 成功返回true
     */
    boolean syncBossKgStatus(Long id);
}
