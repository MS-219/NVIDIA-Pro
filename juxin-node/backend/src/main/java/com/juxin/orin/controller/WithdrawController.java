package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Withdraw;
import com.juxin.orin.service.IWithdrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 提现控制器
 */
@RestController
@RequestMapping("/api/withdraw")
public class WithdrawController {

    @Autowired
    private IWithdrawService withdrawService;

    @Autowired
    private com.juxin.orin.service.IAppUserService appUserService;

    @Autowired
    private com.juxin.orin.service.IBossKgService bossKgService;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    // ========== 小程序端 API ==========

    /**
     * 获取钱包信息 (包含已保存的收款码)
     */
    @GetMapping("/wallet")
    public Result<Object> getWallet(@RequestParam Long userId) {
        // 使用实现类中的扩展方法获取包含二维码的信息
        if (withdrawService instanceof com.juxin.orin.service.impl.WithdrawServiceImpl) {
            return Result
                    .success(((com.juxin.orin.service.impl.WithdrawServiceImpl) withdrawService).getFullWalletInfo(userId));
        }
        return Result.success(withdrawService.getWalletInfo(userId));
    }

    /**
     * 保存收款信息（收款码/银行卡）
     */
    @PostMapping("/save-payment-info")
    public Result<String> savePaymentInfo(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Integer type = params.get("type") != null ? Integer.valueOf(params.get("type").toString()) : 1;
        String qrCode = (String) params.get("qrCode");
        String bankName = (String) params.get("bankName");
        String bankCardNo = (String) params.get("bankCardNo");
        String bankHolderName = (String) params.get("bankHolderName");

        com.juxin.orin.entity.AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        boolean needUpdate = false;

        // 保存收款码
        if (type == 1 && qrCode != null && !qrCode.isEmpty()) { // 微信
            user.setWxQrCode(qrCode);
            needUpdate = true;
        } else if (type == 2 && qrCode != null && !qrCode.isEmpty()) { // 支付宝
            user.setAliQrCode(qrCode);
            needUpdate = true;
        }

        // 保存银行卡信息
        if (type == 3) {
            if (bankName != null)
                user.setBankName(bankName);
            if (bankCardNo != null)
                user.setBankCardNo(bankCardNo);
            if (bankHolderName != null)
                user.setBankHolderName(bankHolderName);
            needUpdate = true;
        }

        if (needUpdate) {
            boolean success = appUserService.updateById(user);
            return success ? Result.success("保存成功") : Result.error("保存失败");
        }

        return Result.success("无需更新");
    }

    /**
     * 申请提现
     */
    @PostMapping("/apply")
    public Result<String> apply(@RequestBody Map<String, Object> params) {
        String allowedDays = configService.getConfig("withdraw.allowedDays", "");
        if (allowedDays != null && !allowedDays.trim().isEmpty()) {
            int today = java.time.LocalDate.now().getDayOfWeek().getValue();
            java.util.List<Integer> configuredDays = java.util.Arrays.stream(allowedDays.split(","))
                    .map(String::trim)
                    .filter(day -> day.matches("[1-7]"))
                    .map(Integer::parseInt)
                    .distinct()
                    .sorted()
                    .toList();
            if (!configuredDays.isEmpty() && !configuredDays.contains(today)) {
                String[] names = { "", "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
                String dayText = configuredDays.stream()
                        .map(day -> names[day])
                        .collect(java.util.stream.Collectors.joining("、"));
                return Result.error("今日暂不可申请，允许提现日为" + dayText);
            }
        }

        Long userId = Long.valueOf(params.get("userId").toString());

        // 检查用户是否被禁止提现
        com.juxin.orin.entity.AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (Boolean.TRUE.equals(user.getWithdrawDisabled())) {
            return Result.error("您的提现功能已被限制，请联系客服");
        }

        // 提现必须先完成实名签约；后台仍可在审核后选择佣金保线上打款或线下打款。
        if (bossKgService.isEnabled() && !bossKgService.isUserContracted(userId)) {
            return Result.error("提现前请先完成实名签约");
        }

        BigDecimal amount = new BigDecimal(params.get("amount").toString());
        Integer type = params.get("type") != null ? Integer.valueOf(params.get("type").toString()) : 1;
        String account = (String) params.get("account");
        String realName = (String) params.get("realName");
        String qrCode = (String) params.get("qrCode");

        // 验证规则：
        // 微信/支付宝(type=1,2)：二维码必填 + 姓名必填
        // 银行卡(type=3)：账号必填 + 姓名必填
        if ((type == 1 || type == 2) && (qrCode == null || qrCode.isEmpty())) {
            return Result.error("请上传收款二维码");
        }
        if (type == 3 && (account == null || account.isEmpty())) {
            return Result.error("请填写银行卡号");
        }
        // 银行卡号格式校验：必须为15-19位纯数字
        if (type == 3 && account != null && !account.matches("\\d{15,19}")) {
            return Result.error("银行卡号格式不正确，请输入15-19位数字");
        }
        if (realName == null || realName.isEmpty()) {
            return Result.error("请填写收款人姓名");
        }

        String error = withdrawService.applyWithdraw(userId, amount, type, account, realName, qrCode);
        if (error != null) {
            return Result.error(error);
        }
        return Result.success("提现申请已提交，请等待审核");
    }

    /**
     * 获取提现记录
     */
    @GetMapping("/list")
    public Result<Object> list(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(withdrawService.getUserWithdrawList(userId, page, size));
    }

    // ========== 管理后台 API ==========

    /**
     * 验证管理员权限的辅助方法
     */
    private String validateAdminToken(String token) {
        if (token == null || token.isEmpty()) {
            return "未登录，请先登录";
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return "登录已过期，请重新登录";
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return "无权限访问此接口";
        }
        return null;
    }

    /**
     * 获取提现申请列表（管理后台用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/admin/list")
    public Result<Object> adminList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Page<Withdraw> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Withdraw> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Withdraw::getUserId, userId);
        }
        if (status != null) {
            if (status == 4) {
                wrapper.eq(Withdraw::getStatus, 4);
                if (userId == null) {
                    // 全局列表仅显示24小时内的失败订单。
                    wrapper.gt(Withdraw::getUpdateTime, java.time.LocalDateTime.now().minusHours(24));
                }
            } else {
                wrapper.eq(Withdraw::getStatus, status);
            }
        } else if (userId == null) {
            // "全部"列表时，排除掉24小时之前的失败订单，保持列表整洁
            wrapper.apply("(status != 4 OR update_time > {0})",
                    java.time.LocalDateTime.now().minusHours(24));
        }
        // 按提现方式筛选：1=微信, 2=支付宝, 3=银行卡
        if (type != null) {
            wrapper.eq(Withdraw::getType, type);
        }
        if (userType != null && !userType.isEmpty()) {
            if (!"personal".equals(userType) && !"company".equals(userType)) {
                return Result.error("用户类型不正确");
            }
            wrapper.inSql(Withdraw::getUserId,
                    "SELECT id FROM app_user WHERE deleted = 0 AND user_type = '" + userType + "'");
        }
        wrapper.orderByDesc(Withdraw::getCreateTime);

        IPage<Withdraw> result = withdrawService.page(pageParam, wrapper);

        // 补充用户信息
        for (Withdraw record : result.getRecords()) {
            if (record.getUserId() != null) {
                com.juxin.orin.entity.AppUser user = appUserService.getById(record.getUserId());
                if (user != null) {
                    record.setNickname(user.getNickname());
                    record.setAvatarUrl(user.getAvatarUrl());
                    record.setUserType(user.getUserType());
                }
            }

            // 补充签约状态
            com.juxin.orin.entity.UserContract contract = bossKgService.getUserContract(record.getUserId());
            if (contract != null) {
                record.setContractStatus(contract.getStatus());
                record.setContractStatusText(getStatusText(contract.getStatus()));
            } else {
                record.setContractStatus(0);
                record.setContractStatusText("待签约");
            }

            applyPendingPayoutDisplayAmount(record);
        }

        return Result.success(result);
    }

    /**
     * 获取提现统计（管理后台用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/admin/stats")
    public Result<Object> stats(
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        long pending = withdrawService.lambdaQuery().eq(Withdraw::getStatus, 0).count();
        long approved = withdrawService.lambdaQuery().eq(Withdraw::getStatus, 1).count();
        long rejected = withdrawService.lambdaQuery().eq(Withdraw::getStatus, 2).count();
        long paid = withdrawService.lambdaQuery().eq(Withdraw::getStatus, 3).count();
        // 失败状态仅统计24小时内的
        long failed = withdrawService.lambdaQuery()
                .eq(Withdraw::getStatus, 4)
                .gt(Withdraw::getUpdateTime, java.time.LocalDateTime.now().minusHours(24))
                .count();

        // 待审核金额（使用实际到账金额，便于预估真正需要准备的打款金额）
        BigDecimal pendingAuditAmount = withdrawService.lambdaQuery()
                .eq(Withdraw::getStatus, 0)
                .list()
                .stream()
                .map(this::getExpectedPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算总提现金额（所有已通过+已打款的，不含拒绝的）
        BigDecimal totalAmount = withdrawService.lambdaQuery()
                .in(Withdraw::getStatus, java.util.Arrays.asList(1, 3))
                .list()
                .stream()
                .map(Withdraw::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 待打款金额（已通过但未打款的）
        BigDecimal pendingPayAmount = withdrawService.lambdaQuery()
                .eq(Withdraw::getStatus, 1)
                .list()
                .stream()
                .map(this::getExpectedPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 已打款金额
        BigDecimal paidAmount = withdrawService.lambdaQuery()
                .eq(Withdraw::getStatus, 3)
                .list()
                .stream()
                .map(this::getActualOrAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("rejected", rejected);
        result.put("paid", paid);
        result.put("failed", failed);
        result.put("total", pending + approved + rejected + paid + failed);
        result.put("pendingAuditAmount", pendingAuditAmount);
        result.put("totalAmount", totalAmount);
        result.put("pendingPayAmount", pendingPayAmount);
        result.put("paidAmount", paidAmount);

        return Result.success(result);
    }

    /**
     * 审核通过（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin/approve/{id}")
    public Result<String> approve(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Long adminId = com.juxin.orin.util.JwtUtil.getUserId(
                token.startsWith("Bearer ") ? token.substring(7) : token);
        boolean success = withdrawService.approve(id, adminId != null ? adminId : 1L);
        return success ? Result.success("审核通过") : Result.error("操作失败");
    }

    /**
     * 批量审核通过（管理员专用）
     */
    @PostMapping("/admin/batch-approve")
    public Result<Object> batchApprove(
            @RequestBody java.util.List<Long> ids,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要审核的记录");
        }

        Long adminId = com.juxin.orin.util.JwtUtil.getUserId(
                token.startsWith("Bearer ") ? token.substring(7) : token);

        int success = 0;
        int fail = 0;
        for (Long id : ids) {
            try {
                boolean ok = withdrawService.approve(id, adminId != null ? adminId : 1L);
                if (ok) success++;
                else fail++;
            } catch (Exception e) {
                fail++;
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("total", ids.size());
        return Result.success(result);
    }

    /**
     * 审核拒绝（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin/reject/{id}")
    public Result<String> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Long adminId = com.juxin.orin.util.JwtUtil.getUserId(
                token.startsWith("Bearer ") ? token.substring(7) : token);
        String remark = params != null ? params.get("remark") : null;
        boolean success = withdrawService.reject(id, adminId != null ? adminId : 1L, remark);
        return success ? Result.success("已拒绝") : Result.error("操作失败");
    }

    /**
     * 确认打款（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin/paid/{id}")
    public Result<String> confirmPaid(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Long adminId = com.juxin.orin.util.JwtUtil.getUserId(
                token.startsWith("Bearer ") ? token.substring(7) : token);
        boolean success = withdrawService.confirmPaid(id, adminId != null ? adminId : 1L);
        return success ? Result.success("已确认打款") : Result.error("操作失败");
    }

    private String getStatusText(Integer status) {
        if (status == null)
            return "待签约";
        switch (status) {
            case 1:
                return "已签约";
            case 2:
                return "签约失败";
            case 3:
                return "签约中";
            default:
                return "待签约";
        }
    }

    private void applyPendingPayoutDisplayAmount(Withdraw withdraw) {
        if (withdraw == null) {
            return;
        }
        boolean waitingForPayoutChoice = (withdraw.getStatus() != null && (withdraw.getStatus() == 0 || withdraw.getStatus() == 1))
                && withdraw.getBossKgState() == null
                && withdraw.getBossKgBatchId() == null;
        if (waitingForPayoutChoice) {
            BigDecimal amount = getAmountOrZero(withdraw.getAmount());
            withdraw.setFee(BigDecimal.ZERO);
            withdraw.setActualAmount(amount);
        }
    }

    private BigDecimal getExpectedPayoutAmount(Withdraw withdraw) {
        if (withdraw == null) {
            return BigDecimal.ZERO;
        }
        boolean hasOnlinePayout = withdraw.getBossKgState() != null || withdraw.getBossKgBatchId() != null;
        return hasOnlinePayout ? getActualOrAmount(withdraw) : getAmountOrZero(withdraw.getAmount());
    }

    private BigDecimal getActualOrAmount(Withdraw withdraw) {
        if (withdraw == null) {
            return BigDecimal.ZERO;
        }
        return withdraw.getActualAmount() != null ? withdraw.getActualAmount() : getAmountOrZero(withdraw.getAmount());
    }

    private BigDecimal getAmountOrZero(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
