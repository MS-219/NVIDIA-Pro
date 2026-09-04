package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.service.IBossKgService;
import com.juxin.orin.service.IWithdrawService;
import com.juxin.orin.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 佣金保管理后台控制器
 * 仅供管理员使用
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/bosskg")
public class AdminBossKgController {

    @Autowired
    private IBossKgService bossKgService;

    @Autowired
    private IWithdrawService withdrawService;
    private final AtomicBoolean pendingSyncRunning = new AtomicBoolean(false);

    /**
     * 获取佣金保状态信息
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus(HttpServletRequest request) {
        // 验证管理员权限
        if (!isAdmin(request)) {
            return Result.error("无权限访问");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("enabled", bossKgService.isEnabled());

        // 查询商户余额
        if (bossKgService.isEnabled()) {
            Long balanceFen = bossKgService.queryBalance();
            if (balanceFen != null) {
                // 转换为元
                BigDecimal balanceYuan = new BigDecimal(balanceFen).divide(new BigDecimal("100"));
                result.put("balance", balanceYuan);
            } else {
                result.put("balance", null);
            }
        }

        return Result.success(result);
    }

    /**
     * 通过佣金保发起付款
     */
    @PostMapping("/pay/{id}")
    public Result<String> payViaBossKg(
            @PathVariable Long id,
            HttpServletRequest request) {

        // 验证管理员权限
        Long adminId = getAdminId(request);
        if (adminId == null) {
            return Result.error("请先登录");
        }

        String error = withdrawService.payViaBossKg(id, adminId);
        if (error != null) {
            return Result.error(error);
        }

        return Result.success("付款请求已提交");
    }

    /**
     * 同步佣金保付款状态
     */
    @PostMapping("/sync/{id}")
    public Result<String> syncStatus(@PathVariable Long id, HttpServletRequest request) {
        // 验证管理员权限
        if (!isAdmin(request)) {
            return Result.error("无权限访问");
        }

        boolean success = withdrawService.syncBossKgStatus(id);
        if (success) {
            return Result.success("同步成功");
        } else {
            return Result.error("同步失败");
        }
    }

    /**
     * 批量同步付款中的订单状态
     */
    @PostMapping("/sync-pending")
    public Result<Map<String, Object>> syncPendingOrders(HttpServletRequest request) {
        // 验证管理员权限
        if (!isAdmin(request)) {
            return Result.error("无权限访问");
        }

        if (!pendingSyncRunning.compareAndSet(false, true)) {
            return Result.success(Map.of(
                    "skipped", true,
                    "message", "同步任务正在执行"));
        }

        try {
            // 查询所有佣金保状态为"付款中"的订单
            java.util.List<com.juxin.orin.entity.Withdraw> pendingList = withdrawService.lambdaQuery()
                    .eq(com.juxin.orin.entity.Withdraw::getBossKgState, 1)
                    .isNotNull(com.juxin.orin.entity.Withdraw::getBossKgBatchId)
                    .list();

            int successCount = 0;
            int failCount = 0;

            for (com.juxin.orin.entity.Withdraw withdraw : pendingList) {
                boolean success = withdrawService.syncBossKgStatus(withdraw.getId());
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("total", pendingList.size());
            result.put("success", successCount);
            result.put("fail", failCount);

            return Result.success(result);
        } finally {
            pendingSyncRunning.set(false);
        }
    }

    /**
     * 批量通过佣金保发起付款
     */
    @PostMapping("/batch-pay")
    public Result<Map<String, Object>> batchPay(
            @RequestBody java.util.List<Long> ids,
            HttpServletRequest request) {

        Long adminId = getAdminId(request);
        if (adminId == null) {
            return Result.error("请先登录");
        }

        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要打款的记录");
        }
        java.util.List<Long> uniqueIds = ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return Result.error("请选择要打款的记录");
        }

        int success = 0;
        int fail = 0;
        StringBuilder errors = new StringBuilder();
        for (Long id : uniqueIds) {
            try {
                String error = withdrawService.payViaBossKg(id, adminId);
                if (error == null) {
                    success++;
                } else {
                    fail++;
                    if (errors.length() < 200) {
                        errors.append("ID").append(id).append(":").append(error).append("; ");
                    }
                }
            } catch (Exception e) {
                fail++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("total", uniqueIds.size());
        result.put("duplicate", ids.size() - uniqueIds.size());
        if (errors.length() > 0) {
            result.put("errors", errors.toString());
        }

        return Result.success(result);
    }

    /**
     * 验证是否为管理员
     */
    private boolean isAdmin(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            return false;
        }
        String userType = JwtUtil.getUserType(token);
        return "admin".equals(userType);
    }

    /**
     * 获取管理员ID
     */
    private Long getAdminId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (!"admin".equals(JwtUtil.getUserType(token))) {
            return null;
        }
        return JwtUtil.getUserId(token);
    }
}
