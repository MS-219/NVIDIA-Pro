package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceEarnings;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.service.IDeviceEarningsService;
import com.juxin.orin.service.IDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 收益管理控制器（管理后台用）
 */
@RestController
@RequestMapping("/api/earnings")
public class EarningsController {

    @Autowired
    private IDeviceEarningsService earningsService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private com.juxin.orin.service.IAppUserService appUserService;

    /**
     * 获取用户收益列表（小程序用）
     */
    @GetMapping("/user/list")
    public Result<Object> userList(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        Page<DeviceEarnings> pageParam = new Page<>(page, size);

        IPage<DeviceEarnings> result = earningsService.lambdaQuery()
                .eq(DeviceEarnings::getUserId, userId)
                .orderByDesc(DeviceEarnings::getCreateTime)
                .page(pageParam);

        // 补充设备信息
        java.util.List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (DeviceEarnings record : result.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            item.put("deviceId", record.getDeviceId());
            item.put("amount", record.getAmount());
            item.put("earnings", record.getAmount().toString()); // 为前端转换格式
            item.put("earningsDate", record.getDate());
            item.put("createTime", record.getCreateTime()); // 具体发放时间

            if (record.getDeviceId() != null) {
                Device device = deviceService.getById(record.getDeviceId());
                if (device != null) {
                    item.put("deviceSn", device.getBindCode() != null ? device.getBindCode() : device.getSn());
                } else {
                    item.put("deviceSn", "未知设备");
                }
            } else {
                item.put("deviceSn", "未知设备");
            }
            records.add(item);
        }

        Map<String, Object> pageResult = new HashMap<>();
        pageResult.put("records", records);
        pageResult.put("total", result.getTotal());
        pageResult.put("hasMore", result.getPages() > result.getCurrent());

        return Result.success(pageResult);
    }

    /**
     * 获取用户月收益汇总（小程序用）
     */
    @GetMapping("/user/monthly")
    public Result<Object> userMonthlyEarnings(@RequestParam Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DeviceEarnings> deviceWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        deviceWrapper.select("DATE_FORMAT(date, '%Y-%m') AS month",
                "SUM(amount) AS totalAmount",
                "COUNT(DISTINCT device_id) AS deviceCount")
                .eq("user_id", userId)
                .groupBy("DATE_FORMAT(date, '%Y-%m')");
        java.util.List<Map<String, Object>> deviceRows = earningsService.getBaseMapper().selectMaps(deviceWrapper);

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.juxin.orin.entity.InviteReward> rewardWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        rewardWrapper.select("DATE_FORMAT(create_time, '%Y-%m') AS month",
                "SUM(reward) AS totalAmount",
                "COUNT(DISTINCT device_id) AS deviceCount")
                .eq("inviter_id", userId)
                .eq("reward_type", "earnings")
                .groupBy("DATE_FORMAT(create_time, '%Y-%m')");
        java.util.List<Map<String, Object>> rewardRows = inviteRewardMapper.selectMaps(rewardWrapper);

        java.util.Map<String, BigDecimal> deviceEarningsByMonth = new java.util.HashMap<>();
        java.util.Map<String, BigDecimal> rewardsByMonth = new java.util.HashMap<>();
        java.util.Map<String, Long> deviceCountByMonth = new java.util.HashMap<>();
        java.util.Map<String, Long> rewardDeviceCountByMonth = new java.util.HashMap<>();

        for (Map<String, Object> row : deviceRows) {
            String month = row.get("month").toString();
            deviceEarningsByMonth.put(month, toBigDecimal(row.get("totalAmount")));
            deviceCountByMonth.put(month, toLong(row.get("deviceCount")));
        }

        for (Map<String, Object> row : rewardRows) {
            String month = row.get("month").toString();
            rewardsByMonth.put(month, toBigDecimal(row.get("totalAmount")));
            rewardDeviceCountByMonth.put(month, toLong(row.get("deviceCount")));
        }

        // 合并所有月份
        java.util.Set<String> months = new java.util.HashSet<>(deviceEarningsByMonth.keySet());
        months.addAll(rewardsByMonth.keySet());

        java.util.List<java.util.Map<String, Object>> monthlyList = new java.util.ArrayList<>();
        for (String month : months) {
            java.util.Map<String, Object> monthData = new java.util.HashMap<>();
            BigDecimal deviceTotal = deviceEarningsByMonth.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal rewardTotal = rewardsByMonth.getOrDefault(month, BigDecimal.ZERO);

            monthData.put("month", month);
            monthData.put("deviceAmount", deviceTotal.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            monthData.put("rewardAmount", rewardTotal.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            monthData.put("totalAmount",
                    deviceTotal.add(rewardTotal).setScale(2, java.math.RoundingMode.HALF_UP).toString());
            monthData.put("deviceCount", deviceCountByMonth.getOrDefault(month, 0L));
            monthData.put("rewardDeviceCount", rewardDeviceCountByMonth.getOrDefault(month, 0L));
            monthlyList.add(monthData);
        }

        // 按月份降序排序
        monthlyList.sort((a, b) -> ((String) b.get("month")).compareTo((String) a.get("month")));

        return Result.success(monthlyList);
    }

    /**
     * 获取用户按日汇总收益（每台设备每天的收益总和）
     */
    @GetMapping("/user/daily")
    public Result<Object> userDailyEarnings(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DeviceEarnings> deviceWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        deviceWrapper.select("device_id AS deviceId",
                "date AS earningsDate",
                "SUM(amount) AS totalAmount")
                .eq("user_id", userId)
                .groupBy("device_id", "date");
        java.util.List<Map<String, Object>> deviceRows = earningsService.getBaseMapper().selectMaps(deviceWrapper);

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.juxin.orin.entity.InviteReward> rewardWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        rewardWrapper.select("device_id AS deviceId",
                "DATE(create_time) AS earningsDate",
                "SUM(reward) AS totalAmount",
                "MAX(invitee_id) AS inviteeId")
                .eq("inviter_id", userId)
                .eq("reward_type", "earnings")
                .isNotNull("device_id")
                .groupBy("device_id", "DATE(create_time)");
        java.util.List<Map<String, Object>> rewardRows = inviteRewardMapper.selectMaps(rewardWrapper);

        java.util.Map<String, BigDecimal> deviceEarningsMap = new java.util.HashMap<>();
        java.util.Map<String, BigDecimal> rewardsMap = new java.util.HashMap<>();
        java.util.Map<Long, Long> deviceToInviteeMap = new java.util.HashMap<>();

        for (Map<String, Object> row : deviceRows) {
            Long deviceId = toLong(row.get("deviceId"));
            String date = formatDate(row.get("earningsDate"));
            if (deviceId != null && date != null) {
                deviceEarningsMap.put(deviceId + "_" + date, toBigDecimal(row.get("totalAmount")));
            }
        }

        for (Map<String, Object> row : rewardRows) {
            Long deviceId = toLong(row.get("deviceId"));
            String date = formatDate(row.get("earningsDate"));
            if (deviceId != null && date != null) {
                rewardsMap.put(deviceId + "_" + date, toBigDecimal(row.get("totalAmount")));
                Long inviteeId = toLong(row.get("inviteeId"));
                if (inviteeId != null) {
                    deviceToInviteeMap.put(deviceId, inviteeId);
                }
            }
        }

        // 合并所有 key
        java.util.Set<String> keys = new java.util.HashSet<>(deviceEarningsMap.keySet());
        keys.addAll(rewardsMap.keySet());

        java.util.Map<Long, String> inviteeNameCache = new java.util.HashMap<>();

        java.util.List<java.util.Map<String, Object>> dailyList = new java.util.ArrayList<>();
        java.util.Map<Long, String> deviceSnCache = new java.util.HashMap<>();

        for (String key : keys) {
            String[] parts = key.split("_");
            Long deviceId = Long.parseLong(parts[0]);
            String date = parts[1];

            BigDecimal deviceTotal = deviceEarningsMap.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal rewardTotal = rewardsMap.getOrDefault(key, BigDecimal.ZERO);

            String deviceSn = deviceSnCache.computeIfAbsent(deviceId, id -> {
                Device device = deviceService.getById(id);
                if (device != null) {
                    return device.getBindCode() != null ? device.getBindCode() : device.getSn();
                }
                return "未知设备";
            });

            // 获取分润贡献人名称
            String inviteeName = "";
            if (rewardTotal.compareTo(BigDecimal.ZERO) > 0) {
                Long inviteeId = deviceToInviteeMap.get(deviceId);
                if (inviteeId != null) {
                    inviteeName = inviteeNameCache.computeIfAbsent(inviteeId, id -> {
                        AppUser invitee = appUserService.getById(id);
                        return invitee != null ? (invitee.getNickname() != null ? invitee.getNickname() : "用户" + id)
                                : "";
                    });
                }
            }

            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("deviceId", deviceId);
            item.put("deviceSn", deviceSn);
            item.put("inviteeName", inviteeName);
            item.put("date", date);
            item.put("deviceAmount", deviceTotal.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            item.put("rewardAmount", rewardTotal.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            item.put("amount", deviceTotal.add(rewardTotal).setScale(2, java.math.RoundingMode.HALF_UP).toString());
            dailyList.add(item);
        }

        // 按日期降序排序
        dailyList.sort((a, b) -> ((String) b.get("date")).compareTo((String) a.get("date")));

        // 分页处理
        int total = dailyList.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        java.util.List<java.util.Map<String, Object>> pageData = start < total ? dailyList.subList(start, end)
                : new java.util.ArrayList<>();

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("records", pageData);
        result.put("total", total);
        result.put("hasMore", end < total);

        return Result.success(result);
    }

    /**
     * 获取收益列表（管理后台用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/admin/list")
    public Result<Object> adminList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：仅管理员可访问
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限访问此接口");
        }

        Page<DeviceEarnings> pageParam = new Page<>(page, size);

        com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<DeviceEarnings> queryWrapper = earningsService
                .lambdaQuery();

        // 日期筛选
        if (startDate != null && !startDate.isEmpty()) {
            LocalDate start = LocalDate.parse(startDate.substring(0, 10));
            queryWrapper.ge(DeviceEarnings::getCreateTime, start.atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            LocalDate end = LocalDate.parse(endDate.substring(0, 10));
            queryWrapper.le(DeviceEarnings::getCreateTime, end.atTime(java.time.LocalTime.MAX));
        }

        queryWrapper.orderByDesc(DeviceEarnings::getCreateTime);

        IPage<DeviceEarnings> result = queryWrapper.page(pageParam);

        // 构建返回结果，补充设备 SN 信息
        java.util.List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (DeviceEarnings record : result.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            item.put("deviceId", record.getDeviceId());
            item.put("userId", record.getUserId());
            item.put("amount", record.getAmount());
            item.put("createTime", record.getCreateTime());
            item.put("date", record.getDate());
            item.put("status", 1);
            item.put("hours", 1);

            if (record.getDeviceId() != null) {
                Device device = deviceService.getById(record.getDeviceId());
                if (device != null) {
                    item.put("deviceSn", device.getBindCode() != null ? device.getBindCode() : device.getSn());
                } else {
                    item.put("deviceSn", "未知设备");
                }
            } else {
                item.put("deviceSn", "未知设备");
            }

            // 补充用户信息
            if (record.getUserId() != null) {
                com.juxin.orin.entity.AppUser user = appUserService.getById(record.getUserId());
                if (user != null) {
                    item.put("nickname", user.getNickname());
                    item.put("avatarUrl", user.getAvatarUrl());
                }
            }

            records.add(item);
        }

        Map<String, Object> pageResult = new HashMap<>();
        pageResult.put("records", records);
        pageResult.put("total", result.getTotal());

        return Result.success(pageResult);
    }

    /**
     * 获取收益统计数据（管理后台用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/stats")
    public Result<Object> stats(
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：仅管理员可访问
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限访问此接口");
        }

        Map<String, Object> data = new HashMap<>();

        // 累计收益
        Map<String, BigDecimal> systemEarnings = earningsService.getSystemEarnings();
        data.put("totalEarnings", systemEarnings.get("total"));

        // 今日收益
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        BigDecimal todayEarnings = earningsService.lambdaQuery()
                .ge(DeviceEarnings::getCreateTime, todayStart)
                .list()
                .stream()
                .map(DeviceEarnings::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("todayEarnings", todayEarnings);

        // 本月收益
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal monthEarnings = earningsService.lambdaQuery()
                .ge(DeviceEarnings::getCreateTime, monthStart)
                .list()
                .stream()
                .map(DeviceEarnings::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("monthEarnings", monthEarnings);

        // 设备运行时长（假设每条收益记录代表1小时）
        long totalHours = earningsService.count();
        data.put("totalHours", totalHours);

        return Result.success(data);
    }

    @Autowired
    private com.juxin.orin.mapper.InviteRewardMapper inviteRewardMapper;

    /**
     * 获取用户的分润收益列表（小程序用）
     */
    @GetMapping("/user/rewards")
    public Result<Object> userRewards(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        Page<com.juxin.orin.entity.InviteReward> pageParam = new Page<>(page, size);

        // 查询该用户获得的分润记录（作为 inviterId）
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.juxin.orin.entity.InviteReward> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(com.juxin.orin.entity.InviteReward::getInviterId, userId)
                .eq(com.juxin.orin.entity.InviteReward::getRewardType, "earnings") // 只查收益分成类型
                .orderByDesc(com.juxin.orin.entity.InviteReward::getCreateTime);

        IPage<com.juxin.orin.entity.InviteReward> result = inviteRewardMapper.selectPage(pageParam, wrapper);

        // 补充设备和用户信息
        java.util.List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (com.juxin.orin.entity.InviteReward record : result.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            item.put("reward", record.getReward());
            item.put("createTime", record.getCreateTime());

            // 被邀请人信息
            if (record.getInviteeId() != null) {
                com.juxin.orin.entity.AppUser invitee = appUserService.getById(record.getInviteeId());
                if (invitee != null) {
                    item.put("inviteeName", invitee.getNickname());
                    item.put("inviteeId", invitee.getId());
                } else {
                    item.put("inviteeName", "未知用户");
                    item.put("inviteeId", record.getInviteeId());
                }
            }

            // 设备信息
            if (record.getDeviceId() != null) {
                Device device = deviceService.getById(record.getDeviceId());
                if (device != null) {
                    item.put("deviceSn", device.getBindCode() != null ? device.getBindCode() : device.getSn());
                    item.put("deviceId", device.getId());
                } else {
                    item.put("deviceSn", "未知设备");
                    item.put("deviceId", record.getDeviceId());
                }
            } else {
                item.put("deviceSn", "-");
                item.put("deviceId", null);
            }

            records.add(item);
        }

        Map<String, Object> pageResult = new HashMap<>();
        pageResult.put("records", records);
        pageResult.put("total", result.getTotal());
        pageResult.put("hasMore", result.getPages() > result.getCurrent());

        return Result.success(pageResult);
    }

    /**
     * 获取用户收益汇总（管理员专用）
     */
    @GetMapping("/admin/user-summary")
    public Result<Object> getUserSummary(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Page<Map<String, Object>> pageParam = new Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DeviceEarnings> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.select("user_id", "SUM(amount) as totalAmount", "COUNT(*) as recordCount",
                "MAX(create_time) as lastTime");

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.apply("user_id IN (SELECT id FROM app_user "
                    + "WHERE deleted = 0 AND (nickname LIKE {0} OR id LIKE {1}))",
                    "%" + keyword + "%", "%" + keyword + "%");
        }

        wrapper.groupBy("user_id");
        wrapper.orderByDesc("totalAmount");

        IPage<Map<String, Object>> result = earningsService.getBaseMapper().selectMapsPage(pageParam, wrapper);

        // 填充用户信息
        for (Map<String, Object> item : result.getRecords()) {
            Object userIdObj = item.get("user_id");
            if (userIdObj != null) {
                Long userId = Long.valueOf(userIdObj.toString());
                AppUser user = appUserService.getById(userId);
                if (user != null) {
                    item.put("nickname", user.getNickname());
                    item.put("avatarUrl", user.getAvatarUrl());
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        return Result.success(data);
    }

    /**
     * 获取设备收益汇总（管理员专用）
     */
    @GetMapping("/admin/device-summary")
    public Result<Object> getDeviceSummary(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Page<Map<String, Object>> pageParam = new Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DeviceEarnings> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.select("device_id", "SUM(amount) as totalAmount", "COUNT(*) as recordCount",
                "MAX(create_time) as lastTime");

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.apply("device_id IN (SELECT id FROM device WHERE sn LIKE {0} OR bind_code LIKE {1})",
                    "%" + keyword + "%", "%" + keyword + "%");
        }

        wrapper.groupBy("device_id");
        wrapper.orderByDesc("totalAmount");

        IPage<Map<String, Object>> result = earningsService.getBaseMapper().selectMapsPage(pageParam, wrapper);

        // 填充设备信息
        for (Map<String, Object> item : result.getRecords()) {
            Object deviceIdObj = item.get("device_id");
            if (deviceIdObj != null) {
                Long deviceId = Long.valueOf(deviceIdObj.toString());
                Device device = deviceService.getById(deviceId);
                if (device != null) {
                    item.put("sn", device.getSn());
                    item.put("bindCode", device.getBindCode());
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        return Result.success(data);
    }

    /**
     * 补偿收益（管理员专用）
     * 用于服务器宕机等异常情况下，为所有设备补发指定小时数的收益
     */
    @PostMapping("/admin/compensate")
    public Result<Object> compensateEarnings(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        // 获取补偿小时数
        Object hoursObj = params.get("hours");
        if (hoursObj == null) {
            return Result.error("请指定补偿小时数");
        }

        int hours;
        try {
            hours = Integer.parseInt(hoursObj.toString());
        } catch (NumberFormatException e) {
            return Result.error("补偿小时数格式不正确");
        }

        if (hours < 1 || hours > 24) {
            return Result.error("补偿小时数必须在 1-24 之间");
        }

        try {
            Map<String, Object> result = earningsService.compensateEarnings(hours);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("补偿收益失败: " + e.getMessage());
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private String formatDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return value.toString();
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

    @Autowired
    private com.juxin.orin.service.IApiMerchantService apiMerchantService;

    /**
     * 修复商户外部用户等级（管理员专用）
     * 将通过API同步的外部用户等级设为其所属商户的等级
     * 用于修复早期同步用户level为null导致收益按最低费率结算的问题
     */
    @PostMapping("/admin/fix-merchant-user-levels")
    public Result<Object> fixMerchantUserLevels(
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        // 查询所有有merchantId但level为null或0的用户
        java.util.List<AppUser> usersToFix = appUserService.lambdaQuery()
                .isNotNull(AppUser::getMerchantId)
                .and(w -> w.isNull(AppUser::getLevel).or().eq(AppUser::getLevel, 0))
                .list();

        int fixedCount = 0;
        for (AppUser user : usersToFix) {
            try {
                com.juxin.orin.entity.ApiMerchant merchant = apiMerchantService.getById(user.getMerchantId());
                if (merchant != null && merchant.getLevel() != null && merchant.getLevel() > 0) {
                    user.setLevel(merchant.getLevel());
                    appUserService.updateById(user);
                    fixedCount++;
                }
            } catch (Exception e) {
                // skip
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalFound", usersToFix.size());
        result.put("fixed", fixedCount);
        return Result.success(result);
    }
}
