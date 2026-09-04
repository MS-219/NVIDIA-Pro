package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IDeviceEarningsService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.mapper.DeviceEarningsMapper;
import com.juxin.orin.mapper.InviteRewardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 统计数据控制器
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

        @Autowired
        private IDeviceService deviceService;

        @Autowired
        private IAppUserService appUserService;

        @Autowired
        private IDeviceEarningsService earningsService;

        @Autowired
        private DeviceEarningsMapper earningsMapper;

        @Autowired
        private InviteRewardMapper inviteRewardMapper;

        /**
         * 获取仪表盘统计数据（管理后台用）
         * 安全修复：需要管理员权限
         */
        @GetMapping("/dashboard")
        public Result<Object> dashboard(
                        @RequestHeader(value = "Authorization", required = false) String token) {

                // 安全验证：仅管理员可访问
                String error = validateAdminToken(token);
                if (error != null) {
                        return Result.error(error);
                }

                Map<String, Object> data = new HashMap<>();

                // 设备统计
                long totalDevices = deviceService.count();
                long onlineDevices = deviceService.lambdaQuery().eq(Device::getStatus, 1).count();
                long offlineDevices = deviceService.lambdaQuery().eq(Device::getStatus, 0).count();
                long boundDevices = deviceService.lambdaQuery().isNotNull(Device::getUserId).count();

                // 用户统计
                long totalUsers = appUserService.count();

                // 收益统计
                Map<String, BigDecimal> earnings = earningsService.getSystemEarnings();

                // 算力统计
                Integer totalHashrate = deviceService.lambdaQuery()
                                .eq(Device::getStatus, 1)
                                .list()
                                .stream()
                                .mapToInt(d -> d.getHashrate() != null ? d.getHashrate() : 0)
                                .sum();

                data.put("device", Map.of(
                                "total", totalDevices,
                                "online", onlineDevices,
                                "offline", offlineDevices,
                                "bound", boundDevices));

                data.put("user", Map.of(
                                "total", totalUsers));

                data.put("earnings", earnings);

                data.put("hashrate", Map.of(
                                "total", totalHashrate));

                return Result.success(data);
        }

        /**
         * 获取用户收益统计（小程序用）
         */
        @GetMapping("/earnings")
        public Result<Object> userEarnings(@RequestParam Long userId) {
                Map<String, BigDecimal> earnings = earningsService.getUserEarnings(userId);

                // 获取用户设备数
                long deviceCount = deviceService.lambdaQuery().eq(Device::getUserId, userId).count();
                long onlineCount = deviceService.lambdaQuery()
                                .eq(Device::getUserId, userId)
                                .eq(Device::getStatus, 1)
                                .count();

                // 获取当前余额
                com.juxin.orin.entity.AppUser user = appUserService.getById(userId);
                BigDecimal currentBalance = user != null && user.getBalance() != null ? user.getBalance()
                                : BigDecimal.ZERO;

                // 计算本月收益
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDate monthStart = today.withDayOfMonth(1);
                BigDecimal monthEarnings = earningsMapper.sumByUserAndDateRange(userId, monthStart, today);
                if (monthEarnings == null) {
                        monthEarnings = BigDecimal.ZERO;
                }

                BigDecimal totalDeviceEarnings = earnings.get("total") != null ? earnings.get("total")
                                : BigDecimal.ZERO;
                BigDecimal totalRewardEarnings = inviteRewardMapper.sumEarningsRewardByInviter(userId);
                if (totalRewardEarnings == null) {
                        totalRewardEarnings = BigDecimal.ZERO;
                }

                Map<String, Object> result = new HashMap<>();
                result.put("yesterday", earnings.get("yesterday"));
                result.put("total", earnings.get("total"));
                result.put("totalEarnings", totalDeviceEarnings.add(totalRewardEarnings));
                result.put("rewardEarnings", totalRewardEarnings);
                result.put("month", monthEarnings);
                result.put("currentBalance", currentBalance);
                result.put("deviceCount", deviceCount);
                result.put("onlineCount", onlineCount);
                // 新增 quota 字段，直接返回数据库中的真实聚芯算力值，供前端创作页使用
                result.put("quota", user != null && user.getQuota() != null ? user.getQuota() : 0);

                return Result.success(result);
        }

        /**
         * 获取统计趋势数据（管理后台用）
         */
        @GetMapping("/trend")
        public Result<Object> trend(
                        @RequestParam(defaultValue = "14") Integer days,
                        @RequestHeader(value = "Authorization", required = false) String token) {

                String error = validateAdminToken(token);
                if (error != null) {
                        return Result.error(error);
                }

                java.util.List<String> dates = new java.util.ArrayList<>();
                java.util.List<BigDecimal> earnings = new java.util.ArrayList<>();
                java.util.List<Long> users = new java.util.ArrayList<>();
                java.util.List<Long> devices = new java.util.ArrayList<>();

                java.time.LocalDate end = java.time.LocalDate.now();
                java.time.LocalDate start = end.minusDays(days - 1);

                for (int i = 0; i < days; i++) {
                        java.time.LocalDate d = start.plusDays(i);
                        dates.add(d.toString());

                        // 当日收益
                        BigDecimal dayEarnings = earningsMapper.sumByDate(d);
                        earnings.add(dayEarnings != null ? dayEarnings : BigDecimal.ZERO);

                        // 当日新增用户
                        long newUsers = appUserService.lambdaQuery()
                                        .ge(com.juxin.orin.entity.AppUser::getCreateTime, d.atStartOfDay())
                                        .le(com.juxin.orin.entity.AppUser::getCreateTime,
                                                        d.atTime(java.time.LocalTime.MAX))
                                        .count();
                        users.add(newUsers);

                        // 当日新增设备
                        long newDevices = deviceService.lambdaQuery()
                                        .ge(Device::getCreateTime, d.atStartOfDay())
                                        .le(Device::getCreateTime, d.atTime(java.time.LocalTime.MAX))
                                        .count();
                        devices.add(newDevices);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("dates", dates);
                result.put("earnings", earnings);
                result.put("users", users);
                result.put("devices", devices);

                return Result.success(result);
        }

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
}
