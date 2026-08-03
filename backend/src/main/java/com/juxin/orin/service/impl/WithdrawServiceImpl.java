package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.UserContract;
import com.juxin.orin.entity.Withdraw;
import com.juxin.orin.mapper.DeviceEarningsMapper;
import com.juxin.orin.mapper.WithdrawMapper;
import com.juxin.orin.service.IWithdrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class WithdrawServiceImpl extends ServiceImpl<WithdrawMapper, Withdraw> implements IWithdrawService {

    private static final BigDecimal MIN_WITHDRAW_AMOUNT = new BigDecimal("0.01");

    @Autowired
    private WithdrawMapper withdrawMapper;

    @Autowired
    private DeviceEarningsMapper earningsMapper;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    @Autowired
    private com.juxin.orin.service.IAppUserService appUserService;

    @Autowired
    private com.juxin.orin.service.IBossKgService bossKgService;

    @Autowired
    private com.juxin.orin.mapper.AppUserMapper appUserMapper;

    @Override
    @Transactional
    public String applyWithdraw(Long userId, BigDecimal amount, Integer type, String account, String realName,
            String qrCode) {
        // 不设置累计提现门槛，仅要求达到人民币最小计价单位。
        if (amount == null || amount.compareTo(MIN_WITHDRAW_AMOUNT) < 0) {
            return "提现金额最低为 0.01 元";
        }

        // 获取用户并校验余额
        com.juxin.orin.entity.AppUser user = appUserService.getById(userId);
        if (user == null) {
            return "用户不存在";
        }

        BigDecimal available = user.getBalance();
        if (amount.compareTo(available) > 0) {
            return "余额不足";
        }

        // 扣除用户余额和算力
        user.setBalance(available.subtract(amount));

        // 获取动态配置的算力兑换比例
        int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));

        int quotaChange = amount.multiply(new java.math.BigDecimal(hashrateRate)).intValue();
        user.setQuota((user.getQuota() != null ? user.getQuota() : 0) - quotaChange);

        boolean updateSuccess = appUserService.updateById(user);
        if (!updateSuccess) {
            return "系统繁忙，请重试";
        }

        // 创建提现记录
        Withdraw withdraw = new Withdraw();
        withdraw.setUserId(userId);
        withdraw.setAmount(amount);
        withdraw.setFee(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        withdraw.setActualAmount(amount.setScale(2, RoundingMode.HALF_UP));
        withdraw.setType(type);
        withdraw.setAccount(account);
        withdraw.setRealName(realName);
        withdraw.setQrCode(qrCode);
        withdraw.setStatus(0); // 待审核
        withdraw.setCreateTime(LocalDateTime.now());
        withdraw.setUpdateTime(LocalDateTime.now());

        this.save(withdraw);

        // 持久化保存用户的收款账号信息
        if (type == 1 && qrCode != null && !qrCode.isEmpty()) { // 微信收款码
            user.setWxQrCode(qrCode);
        } else if (type == 2) { // 支付宝
            if (qrCode != null && !qrCode.isEmpty())
                user.setAliQrCode(qrCode);
            user.setAlipayAccount(account); // 保存支付宝账号
            user.setBankHolderName(realName); // 支付宝也用这个实名
        } else if (type == 3) { // 银行卡
            user.setBankCardNo(account);
            user.setBankHolderName(realName);
            // 注意：applyWithdraw 参数里没有 bankName，如果前端传了最好，或者只能以后补充
        }

        // 统一更新用户信息
        appUserService.updateById(user);

        return null; // 成功返回 null
    }

    @Override
    public Map<String, BigDecimal> getWalletInfo(Long userId) {
        Map<String, BigDecimal> result = new HashMap<>();

        // 累计收益
        BigDecimal totalEarnings = earningsMapper.sumByUser(userId);
        if (totalEarnings == null)
            totalEarnings = BigDecimal.ZERO;

        // 已提现金额
        BigDecimal withdrawn = withdrawMapper.sumWithdrawnByUser(userId);
        if (withdrawn == null)
            withdrawn = BigDecimal.ZERO;

        // 待审核提现金额
        BigDecimal pending = withdrawMapper.sumPendingByUser(userId);
        if (pending == null)
            pending = BigDecimal.ZERO;

        // 可提现余额 - 直接读取用户余额
        com.juxin.orin.entity.AppUser user = appUserService.getById(userId);
        BigDecimal available = (user != null && user.getBalance() != null) ? user.getBalance() : BigDecimal.ZERO;

        result.put("total", totalEarnings);
        result.put("withdrawn", withdrawn);
        result.put("pending", pending);
        result.put("available", available);

        return result;
    }

    /**
     * 扩展：获取包含收款码的钱包详细信息
     */
    public Map<String, Object> getFullWalletInfo(Long userId) {
        Map<String, BigDecimal> basic = getWalletInfo(userId);
        Map<String, Object> result = new HashMap<>(basic);

        com.juxin.orin.entity.AppUser user = appUserService.getById(userId);
        if (user != null) {
            result.put("wxQrCode", user.getWxQrCode());
            result.put("aliQrCode", user.getAliQrCode());
            result.put("savedRealName",
                    user.getBankHolderName() != null ? user.getBankHolderName() : user.getNickname());
            result.put("bankCardNo", user.getBankCardNo());
            result.put("bankHolderName", user.getBankHolderName());
            result.put("alipayAccount", user.getAlipayAccount());

            // 获取签约实名信息
            UserContract contract = bossKgService.getUserContract(userId);
            if (contract != null && contract.getStatus() == 1) { // 1=已签约
                result.put("contractRealName", contract.getRealName());
            }
        }
        return result;
    }

    @Override
    public Page<Withdraw> getUserWithdrawList(Long userId, Integer page, Integer size) {
        Page<Withdraw> pageParam = new Page<>(page, size);
        return this.lambdaQuery()
                .eq(Withdraw::getUserId, userId)
                .orderByDesc(Withdraw::getCreateTime)
                .page(pageParam);
    }

    @Override
    @Transactional
    public boolean approve(Long id, Long auditorId) {
        Withdraw withdraw = this.getById(id);
        if (withdraw == null || withdraw.getStatus() != 0) {
            return false;
        }

        withdraw.setStatus(1); // 已通过
        withdraw.setAuditorId(auditorId);
        withdraw.setAuditTime(LocalDateTime.now());
        withdraw.setUpdateTime(LocalDateTime.now());

        return this.updateById(withdraw);
    }

    @Override
    @Transactional
    public boolean reject(Long id, Long auditorId, String remark) {
        Withdraw withdraw = this.getById(id);
        if (withdraw == null || (withdraw.getStatus() != 0 && withdraw.getStatus() != 1 && withdraw.getStatus() != 4)) {
            return false;
        }
        if (hasActiveBossKgPayment(withdraw)) {
            return false;
        }

        withdraw.setStatus(2); // 已拒绝
        // 清空佣金保相关信息（如果有的话），防止残留数据
        withdraw.setBossKgBatchId(null);
        withdraw.setBossKgOrderNo(null);
        withdraw.setBossKgState(null);
        withdraw.setAuditorId(auditorId);
        withdraw.setAuditTime(LocalDateTime.now());
        withdraw.setRemark(remark);
        withdraw.setUpdateTime(LocalDateTime.now());

        boolean success = this.updateById(withdraw);

        // 拒绝后，返还金额给用户
        if (success) {
            com.juxin.orin.entity.AppUser user = appUserService.getById(withdraw.getUserId());
            if (user != null) {
                user.setBalance(user.getBalance().add(withdraw.getAmount()));

                // 获取动态配置的算力兑换比例
                int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));

                int quotaChange = withdraw.getAmount().multiply(new java.math.BigDecimal(hashrateRate)).intValue();
                user.setQuota((user.getQuota() != null ? user.getQuota() : 0) + quotaChange);
                appUserService.updateById(user);
            }
        }

        return success;
    }

    @Override
    @Transactional
    public boolean confirmPaid(Long id, Long auditorId) {
        Withdraw withdraw = this.getById(id);
        if (withdraw == null || (withdraw.getStatus() != 1 && withdraw.getStatus() != 4)) {
            return false;
        }
        if (hasActiveBossKgPayment(withdraw)) {
            return false;
        }

        applyOfflinePayoutAmount(withdraw);
        withdraw.setStatus(3); // 已打款
        withdraw.setAuditorId(auditorId);
        withdraw.setProcessTime(LocalDateTime.now());
        withdraw.setUpdateTime(LocalDateTime.now());

        return this.updateById(withdraw);
    }

    @Override
    @Transactional
    public String payViaBossKg(Long id, Long auditorId) {
        Withdraw withdraw = this.getById(id);
        if (withdraw == null) {
            return "提现记录不存在";
        }

        // 已通过、失败的订单可以发起付款；待审核必须先通过审核。
        if (withdraw.getStatus() == 0) {
            return "请先审核通过后再付款";
        }
        if (withdraw.getStatus() != 1 && withdraw.getStatus() != 4) {
            return "当前状态不允许付款";
        }
        if (hasActiveBossKgPayment(withdraw)) {
            return "该提现已发起佣金保付款，请先同步状态，避免重复打款";
        }

        // 检查佣金保是否启用
        if (!bossKgService.isEnabled()) {
            return "佣金保服务未启用";
        }

        // 获取用户信息
        com.juxin.orin.entity.AppUser user = appUserMapper.selectById(withdraw.getUserId());
        if (user == null) {
            return "用户不存在";
        }

        // 检查用户是否已签约
        UserContract contract = bossKgService.getUserContract(withdraw.getUserId());
        if (contract == null || contract.getStatus() != 1) { // 1=已签约
            return "用户未完成签约，请先完成签约后再付款";
        }

        // 补充提现记录信息 - 强制使用签约的真实姓名，防止用户填错
        withdraw.setRealName(contract.getRealName()); // 【关键修复】使用签约实名
        withdraw.setIdCard(contract.getIdCard()); // 使用签约身份证

        withdraw.setMobile(contract.getMobile() != null ? contract.getMobile() : user.getPhone());
        if (contract.getPaymentType() != null && contract.getPaymentType() == UserContract.PAY_TYPE_ALIPAY) {
            withdraw.setAlipayAccount(contract.getAlipayAccount());
            withdraw.setBankCardNo(null);
        } else {
            withdraw.setBankCardNo(contract.getBankCardNo());
            withdraw.setAlipayAccount(null);
        }

        applyOnlinePayoutAmount(withdraw);
        if (withdraw.getActualAmount() == null || withdraw.getActualAmount().compareTo(new BigDecimal("10.00")) < 0) {
            return "佣金保单笔实际打款金额最低为 10 元，可改用线下打款";
        }
        Integer originalStatus = withdraw.getStatus();
        boolean reserved = reserveBossKgPayment(withdraw, auditorId);
        if (!reserved) {
            return "该提现正在付款或已发起付款，请刷新后再操作";
        }
        withdraw.setStatus(1);
        withdraw.setBossKgState(1);
        withdraw.setAuditorId(auditorId);
        withdraw.setAuditTime(LocalDateTime.now());
        withdraw.setUpdateTime(LocalDateTime.now());

        // 发起付款
        String error = bossKgService.payment(withdraw, contract.getIdCard());
        if (error != null) {
            Withdraw latest = this.getById(id);
            if (hasBossKgSubmission(latest)) {
                latest.setStatus(1);
                latest.setAuditorId(auditorId);
                latest.setAuditTime(LocalDateTime.now());
                latest.setUpdateTime(LocalDateTime.now());
                this.updateById(latest);
                return null;
            }
            if (isUnknownBossKgPaymentResult(error)) {
                keepBossKgPaymentReservation(id, error);
                return error;
            }
            releaseBossKgPaymentReservation(id, originalStatus);
            return error;
        }

        // 更新状态为已通过(付款中)
        withdraw.setStatus(1);
        withdraw.setAuditorId(auditorId);
        withdraw.setAuditTime(LocalDateTime.now());
        withdraw.setUpdateTime(LocalDateTime.now());
        this.updateById(withdraw);

        return null; // 成功
    }

    private boolean reserveBossKgPayment(Withdraw withdraw, Long auditorId) {
        LocalDateTime now = LocalDateTime.now();
        return this.lambdaUpdate()
                .eq(Withdraw::getId, withdraw.getId())
                .in(Withdraw::getStatus, 1, 4)
                .and(w -> w.isNull(Withdraw::getBossKgState)
                        .or()
                        .eq(Withdraw::getBossKgState, 4)
                        .or()
                        .eq(Withdraw::getBossKgState, 7))
                .set(Withdraw::getRealName, withdraw.getRealName())
                .set(Withdraw::getIdCard, withdraw.getIdCard())
                .set(Withdraw::getMobile, withdraw.getMobile())
                .set(Withdraw::getBankCardNo, withdraw.getBankCardNo())
                .set(Withdraw::getAlipayAccount, withdraw.getAlipayAccount())
                .set(Withdraw::getFee, withdraw.getFee())
                .set(Withdraw::getActualAmount, withdraw.getActualAmount())
                .set(Withdraw::getStatus, 1)
                .set(Withdraw::getBossKgState, 1)
                .set(Withdraw::getBossKgBatchId, null)
                .set(Withdraw::getBossKgOrderNo, null)
                .set(Withdraw::getBossKgFee, null)
                .set(Withdraw::getBossKgUserFee, null)
                .set(Withdraw::getBossKgActualAmount, null)
                .set(Withdraw::getBossKgFailed, false)
                .set(Withdraw::getAuditorId, auditorId)
                .set(Withdraw::getAuditTime, now)
                .set(Withdraw::getUpdateTime, now)
                .update();
    }

    private void releaseBossKgPaymentReservation(Long id, Integer originalStatus) {
        this.lambdaUpdate()
                .eq(Withdraw::getId, id)
                .eq(Withdraw::getBossKgState, 1)
                .isNull(Withdraw::getBossKgBatchId)
                .set(Withdraw::getStatus, originalStatus)
                .set(Withdraw::getBossKgState, null)
                .set(Withdraw::getUpdateTime, LocalDateTime.now())
                .update();
    }

    private void keepBossKgPaymentReservation(Long id, String error) {
        this.lambdaUpdate()
                .eq(Withdraw::getId, id)
                .eq(Withdraw::getBossKgState, 1)
                .isNull(Withdraw::getBossKgBatchId)
                .set(Withdraw::getStatus, 1)
                .set(Withdraw::getRejectReason, "佣金保付款结果未知，已锁定防止重复打款：" + error)
                .set(Withdraw::getUpdateTime, LocalDateTime.now())
                .update();
    }

    private boolean isUnknownBossKgPaymentResult(String error) {
        if (error == null) {
            return false;
        }
        return error.contains("请求佣金保平台失败") || error.contains("付款请求异常");
    }

    private boolean hasActiveBossKgPayment(Withdraw withdraw) {
        if (withdraw == null) {
            return false;
        }
        Integer state = withdraw.getBossKgState();
        if (state != null && (state == 1 || state == 3 || state == 6)) {
            return true;
        }
        boolean retryableState = state != null && (state == 4 || state == 7);
        return (withdraw.getBossKgBatchId() != null || withdraw.getBossKgOrderNo() != null) && !retryableState;
    }

    private boolean hasBossKgSubmission(Withdraw withdraw) {
        if (withdraw == null) {
            return false;
        }
        Integer state = withdraw.getBossKgState();
        return withdraw.getBossKgBatchId() != null || withdraw.getBossKgOrderNo() != null
                || (state != null && (state == 3 || state == 6));
    }

    private void applyOfflinePayoutAmount(Withdraw withdraw) {
        BigDecimal amount = withdraw.getAmount() != null ? withdraw.getAmount() : BigDecimal.ZERO;
        withdraw.setFee(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        withdraw.setActualAmount(amount.setScale(2, RoundingMode.HALF_UP));
    }

    private void applyOnlinePayoutAmount(Withdraw withdraw) {
        BigDecimal amount = withdraw.getAmount() != null ? withdraw.getAmount() : BigDecimal.ZERO;
        BigDecimal fee = calculateWithdrawFee(amount);
        BigDecimal actualAmount = amount.subtract(fee).max(BigDecimal.ZERO);
        withdraw.setFee(fee);
        withdraw.setActualAmount(actualAmount.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal calculateWithdrawFee(BigDecimal amount) {
        String withdrawFeeStr = configService.getConfig("earnings.withdrawFee", "1"); // 百分比，如 1 表示 1%
        BigDecimal feeRate = new BigDecimal(withdrawFeeStr).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        return amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public boolean syncBossKgStatus(Long id) {
        Withdraw withdraw = this.getById(id);
        if (withdraw == null || withdraw.getBossKgBatchId() == null) {
            return false;
        }

        // 查询佣金保付款状态
        java.util.Map<String, Object> result = bossKgService.queryPaymentStatus(
                withdraw.getBossKgBatchId(),
                withdraw.getBossKgOrderNo());

        if (result == null || !(Boolean) result.get("success")) {
            return false;
        }

        Integer state = (Integer) result.get("state");
        if (state == null) {
            return false;
        }

        // 更新状态
        withdraw.setBossKgState(state);
        if (result.get("orderNo") != null) {
            withdraw.setBossKgOrderNo((String) result.get("orderNo"));
        }
        if (result.get("fee") != null) {
            Long fee = (Long) result.get("fee");
            withdraw.setBossKgFee(new BigDecimal(fee).divide(new BigDecimal("100")));
        }
        if (result.get("userFee") != null) {
            Long userFee = (Long) result.get("userFee");
            withdraw.setBossKgUserFee(new BigDecimal(userFee).divide(new BigDecimal("100")));
        }
        if (result.get("userDueAmt") != null) {
            Long userDueAmt = (Long) result.get("userDueAmt");
            withdraw.setBossKgActualAmount(new BigDecimal(userDueAmt).divide(new BigDecimal("100")));
        }

        // 更新提现状态
        if (state == 3) {
            // 付款成功
            withdraw.setStatus(3); // 已打款
            withdraw.setProcessTime(LocalDateTime.now());
            // 清除失败标记
            withdraw.setBossKgFailed(false);
        } else if (state == 4) {
            // 付款失败 - 设为失败状态，停止自动重试
            withdraw.setStatus(4); // 标记为失败
            withdraw.setBossKgFailed(true);
            withdraw.setPaymentFailCount(
                    (withdraw.getPaymentFailCount() != null ? withdraw.getPaymentFailCount() : 0) + 1);
            withdraw.setRejectReason((String) result.get("resMsg"));
            // 保留佣金保信息作为记录，但清空 batchId 以便可以重新打款
            withdraw.setBossKgBatchId(null);
        }

        withdraw.setUpdateTime(LocalDateTime.now());
        return this.updateById(withdraw);
    }
}
