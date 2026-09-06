package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.SystemConfig;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.ISystemConfigService;
import com.juxin.orin.service.InviteLevelConfigService;
import com.juxin.orin.util.OrinPowerMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统设置控制器
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @Autowired
    private ISystemConfigService configService;

    @Autowired
    private InviteLevelConfigService inviteLevelConfigService;

    @Autowired
    private IAppUserService appUserService;

    // 配置键常量
    private static final String KEY_DAILY_MIN_RATE = "earnings.dailyMinRate";
    private static final String KEY_DAILY_MAX_RATE = "earnings.dailyMaxRate";
    private static final String KEY_MAX_DAILY_OFFLINE_HOURS = "earnings.maxDailyOfflineHours";
    private static final String LEGACY_KEY_DAILY_RATE = "earnings.dailyRate";
    private static final String LEGACY_KEY_HOURLY_RATE = "earnings.hourlyRate";
    private static final String KEY_HASHRATE_PER_YUAN = "earnings.hashratePerYuan";
    private static final String KEY_MIN_WITHDRAW = "earnings.minWithdraw";
    private static final String KEY_WITHDRAW_FEE = "earnings.withdrawFee";
    private static final String KEY_EARNINGS_RATE = "invite.earningsRate";

    private static final String KEY_SITE_NAME = "system.siteName";
    private static final String KEY_CONTACT_EMAIL = "system.contactEmail";
    private static final String KEY_CONTACT_WECHAT = "system.contactWechat";
    private static final String KEY_CONTACT_WORK_TIME = "system.contactWorkTime";

    private static final String KEY_HEARTBEAT_TIMEOUT = "device.heartbeatTimeout";
    private static final String KEY_HEARTBEAT_INTERVAL = "device.heartbeatInterval";
    private static final String KEY_TASK_POLL_INTERVAL = "device.taskPollInterval";
    private static final String KEY_OFFLINE_THRESHOLD = "device.offlineThreshold";
    private static final String KEY_AUTO_ASSIGN_BUSINESS = "device.autoAssignBusiness";
    private static final String KEY_INITIAL_HASHRATE = "device.initialHashrate";
    private static final String KEY_POWER_MODE = "device.powerMode";

    private static final String KEY_MAINTENANCE_MODE = "system.maintenanceMode";
    private static final String KEY_BANNER_LIST = "system.bannerList";
    private static final String KEY_WITHDRAW_ALLOWED_DAYS = "withdraw.allowedDays"; // 允许提现的星期几,如"1,4"表示周一和周四
    private static final String KEY_WITHDRAW_MONTHLY_START = "withdraw.monthlyStartDay";
    private static final String KEY_WITHDRAW_MONTHLY_END = "withdraw.monthlyEndDay";

    private static final String SUFFIX_NAME = ".name";
    private static final String SUFFIX_RATE = ".rate";
    private static final String SUFFIX_THRESHOLD = ".threshold";

    /**
     * 验证管理员权限的辅助方法
     * 
     * @return null 表示验证通过，否则返回错误信息
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
     * 获取所有设置（管理员专用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/all")
    public Result<Object> getAllSettings(
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：仅管理员可访问
        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Map<String, Object> settings = new HashMap<>();

        // 收益设置
        Map<String, Object> earnings = new HashMap<>();
        earnings.put("dailyMinRate", getDailyRangeRate(KEY_DAILY_MIN_RATE));
        earnings.put("dailyMaxRate", getDailyRangeRate(KEY_DAILY_MAX_RATE));
        earnings.put("maxDailyOfflineHours",
                Double.parseDouble(configService.getConfig(KEY_MAX_DAILY_OFFLINE_HOURS, "24")));
        earnings.put("hashratePerYuan", Integer.parseInt(configService.getConfig(KEY_HASHRATE_PER_YUAN, "100")));
        earnings.put("minWithdraw", 0.01D);
        earnings.put("withdrawFee", Double.parseDouble(configService.getConfig(KEY_WITHDRAW_FEE, "1")));
        earnings.put("earningsRate", Double.parseDouble(configService.getConfig(KEY_EARNINGS_RATE, "0.1")));

        // 分润等级由管理员配置的数量决定；旧数据未设置数量时默认读取 5 级。
        List<Map<String, Object>> levels = new ArrayList<>();
        for (int i = 1; i <= inviteLevelConfigService.getLevelCount(); i++) {
            Map<String, Object> lv = new HashMap<>();
            lv.put("index", i);
            lv.put("name", inviteLevelConfigService.getLevelName(i));
            lv.put("rate", inviteLevelConfigService.getLevelRate(i));
            lv.put("threshold", inviteLevelConfigService.getLevelThreshold(i));
            levels.add(lv);
        }
        settings.put("inviteLevels", levels);
        settings.put("earnings", earnings);

        // 设备设置
        Map<String, Object> device = new HashMap<>();
        device.put("heartbeatTimeout", Integer.parseInt(configService.getConfig(KEY_HEARTBEAT_TIMEOUT, "120")));
        device.put("heartbeatInterval", Integer.parseInt(configService.getConfig(KEY_HEARTBEAT_INTERVAL, "60")));
        device.put("taskPollInterval", Integer.parseInt(configService.getConfig(KEY_TASK_POLL_INTERVAL, "60")));
        device.put("offlineThreshold", Integer.parseInt(configService.getConfig(KEY_OFFLINE_THRESHOLD, "180")));
        device.put("autoAssignBusiness",
                Boolean.parseBoolean(configService.getConfig(KEY_AUTO_ASSIGN_BUSINESS, "true")));
        device.put("initialHashrate", Integer.parseInt(configService.getConfig(KEY_INITIAL_HASHRATE, "100")));
        String powerMode = OrinPowerMode.normalize(configService.getConfig(KEY_POWER_MODE, OrinPowerMode.DEFAULT));
        device.put("powerMode", powerMode != null ? powerMode : OrinPowerMode.DEFAULT);
        settings.put("device", device);

        // 系统设置
        Map<String, Object> system = new HashMap<>();
        system.put("siteName", configService.getConfig(KEY_SITE_NAME, "聚芯算力"));
        system.put("contactEmail", configService.getConfig(KEY_CONTACT_EMAIL, ""));
        system.put("contactWechat", configService.getConfig(KEY_CONTACT_WECHAT, "orin-support"));
        system.put("contactWorkTime", configService.getConfig(KEY_CONTACT_WORK_TIME, "9:00-18:00"));
        system.put("contactWorkTime", configService.getConfig(KEY_CONTACT_WORK_TIME, "9:00-18:00"));
        system.put("maintenanceMode", Boolean.parseBoolean(configService.getConfig(KEY_MAINTENANCE_MODE, "false")));

        // 解析轮播图 JSON
        String bannerJson = configService.getConfig(KEY_BANNER_LIST, "[]");
        try {
            settings.put("banners",
                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(bannerJson, java.util.List.class));
        } catch (Exception e) {
            settings.put("banners", new java.util.ArrayList<>());
        }

        settings.put("system", system);

        // 提现日期限制配置 (1=周一, 7=周日，空字符串表示不限制)
        settings.put("withdrawAllowedDays", configService.getConfig(KEY_WITHDRAW_ALLOWED_DAYS, ""));
        settings.put("withdrawMonthlyStartDay", configService.getConfig(KEY_WITHDRAW_MONTHLY_START, "1"));
        settings.put("withdrawMonthlyEndDay", configService.getConfig(KEY_WITHDRAW_MONTHLY_END, "31"));

        return Result.success(settings);
    }

    /**
     * 保存收益设置（管理员专用）
     * 安全修复：需要管理员权限
     */
    @Transactional
    @PostMapping("/earnings")
    public Result<Object> saveEarningsSettings(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        List<InviteLevelInput> levels = null;
        if (params.get("inviteLevels") != null) {
            try {
                levels = parseInviteLevels(params.get("inviteLevels"));
            } catch (IllegalArgumentException e) {
                return Result.error(e.getMessage());
            }
        }

        Object dailyMinRate = params.get("dailyMinRate");
        Object dailyMaxRate = params.get("dailyMaxRate");
        Object maxDailyOfflineHours = params.get("maxDailyOfflineHours");
        Object legacyRate = params.get("dailyRate") != null ? params.get("dailyRate") : params.get("hourlyRate");
        if (dailyMinRate == null && dailyMaxRate == null && legacyRate != null) {
            dailyMinRate = legacyRate;
            dailyMaxRate = legacyRate;
        }

        BigDecimal dailyMinRateValue;
        BigDecimal dailyMaxRateValue;
        BigDecimal maxDailyOfflineHoursValue;
        try {
            dailyMinRateValue = parseOptionalDecimal(dailyMinRate, "每天收益最低金额");
            dailyMaxRateValue = parseOptionalDecimal(dailyMaxRate, "每天收益最高金额");
            maxDailyOfflineHoursValue = parseOptionalDecimal(maxDailyOfflineHours, "每日累计离线上限");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }

        if (dailyMinRateValue != null) {
            dailyMinRateValue = dailyMinRateValue.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (dailyMaxRateValue != null) {
            dailyMaxRateValue = dailyMaxRateValue.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (maxDailyOfflineHoursValue != null
                && (maxDailyOfflineHoursValue.compareTo(BigDecimal.ZERO) < 0
                        || maxDailyOfflineHoursValue.compareTo(BigDecimal.valueOf(24)) > 0)) {
            return Result.error("每日累计离线上限必须在 0 到 24 小时之间");
        }

        if (dailyMinRateValue != null || dailyMaxRateValue != null) {
            BigDecimal effectiveMin = dailyMinRateValue != null
                    ? dailyMinRateValue
                    : BigDecimal.valueOf(getDailyRangeRate(KEY_DAILY_MIN_RATE));
            BigDecimal effectiveMax = dailyMaxRateValue != null
                    ? dailyMaxRateValue
                    : BigDecimal.valueOf(getDailyRangeRate(KEY_DAILY_MAX_RATE));
            if (effectiveMin.compareTo(BigDecimal.ZERO) < 0) {
                return Result.error("每天收益最低金额不能小于 0");
            }
            if (effectiveMax.compareTo(BigDecimal.ZERO) < 0) {
                return Result.error("每天收益最高金额不能小于 0");
            }
            if (effectiveMin.compareTo(effectiveMax) > 0) {
                return Result.error("每天收益最低金额不能大于最高金额");
            }
        }

        if (dailyMinRateValue != null) {
            configService.setConfig(KEY_DAILY_MIN_RATE, dailyMinRateValue.stripTrailingZeros().toPlainString());
        }
        if (dailyMaxRateValue != null) {
            configService.setConfig(KEY_DAILY_MAX_RATE, dailyMaxRateValue.stripTrailingZeros().toPlainString());
        }
        if (maxDailyOfflineHoursValue != null) {
            configService.setConfig(KEY_MAX_DAILY_OFFLINE_HOURS,
                    maxDailyOfflineHoursValue.stripTrailingZeros().toPlainString());
        }
        if (params.get("hashratePerYuan") != null) {
            configService.setConfig(KEY_HASHRATE_PER_YUAN, params.get("hashratePerYuan").toString());
        }
        if (params.get("minWithdraw") != null) {
            configService.setConfig(KEY_MIN_WITHDRAW, "0.01");
        }
        if (params.get("withdrawFee") != null) {
            configService.setConfig(KEY_WITHDRAW_FEE, params.get("withdrawFee").toString());
        }
        if (params.get("earningsRate") != null) {
            configService.setConfig(KEY_EARNINGS_RATE, params.get("earningsRate").toString());
        }

        // 保存等级设置。数组顺序就是等级顺序，索引由后端连续生成。
        if (levels != null) {
            int previousCount = inviteLevelConfigService.getLevelCount();
            for (int index = 0; index < levels.size(); index++) {
                int level = index + 1;
                InviteLevelInput input = levels.get(index);
                configService.setConfig(InviteLevelConfigService.levelKey(level, SUFFIX_NAME), input.name());
                configService.setConfig(InviteLevelConfigService.levelKey(level, SUFFIX_RATE), input.rate().toPlainString());
                configService.setConfig(InviteLevelConfigService.levelKey(level, SUFFIX_THRESHOLD),
                        String.valueOf(input.threshold()));
            }

            removeObsoleteLevels(levels.size(), previousCount);
            configService.setConfig(InviteLevelConfigService.LEVEL_COUNT_KEY, String.valueOf(levels.size()));
            appUserService.clampUserLevels(levels.size());
            appUserService.updateAllUserLevels();
        }
        return Result.success("收益及等级设置保存成功");
    }

    private List<InviteLevelInput> parseInviteLevels(Object rawLevels) {
        if (!(rawLevels instanceof List<?> list)) {
            throw new IllegalArgumentException("等级配置格式不正确");
        }
        if (list.size() > InviteLevelConfigService.MAX_LEVEL_COUNT) {
            throw new IllegalArgumentException("代理等级不能超过 " + InviteLevelConfigService.MAX_LEVEL_COUNT + " 个");
        }

        List<InviteLevelInput> levels = new ArrayList<>();
        int previousThreshold = -1;
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> level)) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 个等级格式不正确");
            }

            String name = level.get("name") == null ? "" : level.get("name").toString().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 个等级名称不能为空");
            }
            if (name.length() > 50) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 个等级名称不能超过 50 个字符");
            }

            int threshold;
            BigDecimal rate;
            try {
                threshold = new BigDecimal(String.valueOf(level.get("threshold"))).intValueExact();
                rate = new BigDecimal(String.valueOf(level.get("rate")));
            } catch (Exception e) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 个等级的门槛或分润比例格式不正确");
            }
            if (threshold < 0) {
                throw new IllegalArgumentException("等级门槛不能小于 0");
            }
            if (threshold <= previousThreshold) {
                throw new IllegalArgumentException("等级门槛必须按等级严格递增");
            }
            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("分润比例必须在 0% 到 100% 之间");
            }
            levels.add(new InviteLevelInput(name, threshold, rate));
            previousThreshold = threshold;
        }
        return levels;
    }

    private void removeObsoleteLevels(int currentCount, int previousCount) {
        if (currentCount >= previousCount) {
            return;
        }
        List<String> obsoleteKeys = new ArrayList<>();
        for (int level = currentCount + 1; level <= previousCount; level++) {
            obsoleteKeys.add(InviteLevelConfigService.levelKey(level, SUFFIX_NAME));
            obsoleteKeys.add(InviteLevelConfigService.levelKey(level, SUFFIX_RATE));
            obsoleteKeys.add(InviteLevelConfigService.levelKey(level, SUFFIX_THRESHOLD));
        }
        configService.remove(new LambdaQueryWrapper<SystemConfig>().in(SystemConfig::getConfigKey, obsoleteKeys));
    }

    private record InviteLevelInput(String name, int threshold, BigDecimal rate) {
    }

    /**
     * 保存设备设置（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/device")
    public Result<Object> saveDeviceSettings(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        String powerMode = null;
        if (params.get("powerMode") != null) {
            powerMode = OrinPowerMode.normalize(params.get("powerMode"));
            if (powerMode == null) {
                return Result.error("功耗模式仅支持 15W、25W 或 MAXN_SUPER");
            }
        }

        if (params.get("heartbeatTimeout") != null) {
            configService.setConfig(KEY_HEARTBEAT_TIMEOUT, params.get("heartbeatTimeout").toString());
        }
        if (params.get("heartbeatInterval") != null) {
            configService.setConfig(KEY_HEARTBEAT_INTERVAL, params.get("heartbeatInterval").toString());
        }
        if (params.get("taskPollInterval") != null) {
            configService.setConfig(KEY_TASK_POLL_INTERVAL, params.get("taskPollInterval").toString());
        }
        if (params.get("offlineThreshold") != null) {
            configService.setConfig(KEY_OFFLINE_THRESHOLD, params.get("offlineThreshold").toString());
        }
        if (params.get("autoAssignBusiness") != null) {
            configService.setConfig(KEY_AUTO_ASSIGN_BUSINESS, params.get("autoAssignBusiness").toString());
        }
        if (params.get("initialHashrate") != null) {
            configService.setConfig(KEY_INITIAL_HASHRATE, params.get("initialHashrate").toString());
        }
        if (powerMode != null) {
            configService.setConfig(KEY_POWER_MODE, powerMode);
        }
        return Result.success("设备设置保存成功");
    }

    /**
     * 保存系统设置（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/system")
    public Result<Object> saveSystemSettings(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        if (params.get("siteName") != null) {
            configService.setConfig(KEY_SITE_NAME, params.get("siteName").toString());
        }
        if (params.get("contactEmail") != null) {
            configService.setConfig(KEY_CONTACT_EMAIL, params.get("contactEmail").toString());
        }
        if (params.get("contactWechat") != null) {
            configService.setConfig(KEY_CONTACT_WECHAT, params.get("contactWechat").toString());
        }
        if (params.get("contactWorkTime") != null) {
            configService.setConfig(KEY_CONTACT_WORK_TIME, params.get("contactWorkTime").toString());
        }
        if (params.get("maintenanceMode") != null) {
            configService.setConfig(KEY_MAINTENANCE_MODE, params.get("maintenanceMode").toString());
        }
        return Result.success("系统设置保存成功");
    }

    /**
     * 保存轮播图设置（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/banners")
    public Result<Object> saveBannerSettings(
            @RequestBody java.util.List<Map<String, Object>> banners,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(banners);
            configService.setConfig(KEY_BANNER_LIST, json);
            return Result.success("轮播图设置已保存");
        } catch (Exception e) {
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 获取轮播图（小程序公开接口，无需认证）
     */
    @GetMapping("/banners")
    public Result<Object> getBanners() {
        String bannerJson = configService.getConfig(KEY_BANNER_LIST, "[]");
        try {
            java.util.List<?> banners = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(bannerJson, java.util.List.class);
            return Result.success(java.util.Map.of("banners", banners));
        } catch (Exception e) {
            return Result.success(java.util.Map.of("banners", new java.util.ArrayList<>()));
        }
    }

    /**
     * 获取算力兑换比例（小程序调用）
     */
    @GetMapping("/hashrate-rate")
    public Result<Object> getHashrateRate() {
        String rate = configService.getConfig(KEY_HASHRATE_PER_YUAN, "100");
        return Result.success(Integer.parseInt(rate));
    }

    /**
     * 获取系统配置（小程序公开接口，无需认证）
     * 用于小程序帮助中心等页面获取客服微信、工作时间等系统信息
     */
    @GetMapping("/system-config")
    public Result<Object> getSystemConfig() {
        Map<String, Object> system = new HashMap<>();
        system.put("siteName", configService.getConfig(KEY_SITE_NAME, "聚芯算力"));
        system.put("contactWechat", configService.getConfig(KEY_CONTACT_WECHAT, "orin-support"));
        system.put("contactWorkTime", configService.getConfig(KEY_CONTACT_WORK_TIME, "9:00-18:00"));
        system.put("contactEmail", configService.getConfig(KEY_CONTACT_EMAIL, ""));
        return Result.success(system);
    }

    /**
     * 获取收益相关配置（小程序提现页面调用）
     * 返回：算力兑换比例、最低提现金额、提现手续费
     */
    @GetMapping("/earnings-config")
    public Result<Object> getEarningsConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("hashratePerYuan", Integer.parseInt(configService.getConfig(KEY_HASHRATE_PER_YUAN, "100")));
        config.put("minWithdraw", 0.01D);
        config.put("withdrawFee", Double.parseDouble(configService.getConfig(KEY_WITHDRAW_FEE, "1")));
        config.put("dailyMinRate", getDailyRangeRate(KEY_DAILY_MIN_RATE));
        config.put("dailyMaxRate", getDailyRangeRate(KEY_DAILY_MAX_RATE));
        config.put("maxDailyOfflineHours",
                Double.parseDouble(configService.getConfig(KEY_MAX_DAILY_OFFLINE_HOURS, "24")));
        return Result.success(config);
    }

    private double getDailyRangeRate(String key) {
        String legacyHourlyRate = configService.getConfig(LEGACY_KEY_HOURLY_RATE, "2.4");
        String legacyDailyRate = configService.getConfig(LEGACY_KEY_DAILY_RATE, legacyHourlyRate);
        return Double.parseDouble(configService.getConfig(key, legacyDailyRate));
    }

    private BigDecimal parseOptionalDecimal(Object value, String label) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
    }

    /**
     * 保存提现日期限制配置（管理员专用）
     * 安全修复：需要管理员权限
     * 
     * @param params 包含 allowedDays 字段，如 "1,4" 表示周一和周四可提现，空字符串表示不限制
     */
    @PostMapping("/withdraw-days")
    public Result<Object> saveWithdrawDays(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        String allowedDays = params.get("allowedDays") != null ? params.get("allowedDays").toString() : "";
        if (params.containsKey("startDay") || params.containsKey("endDay")) {
            try {
                int start = Integer.parseInt(String.valueOf(params.getOrDefault("startDay", 1)));
                int end = Integer.parseInt(String.valueOf(params.getOrDefault("endDay", 31)));
                if (start < 1 || start > 31 || end < 1 || end > 31 || start > end) {
                    return Result.error("提现日期必须是每月 1 至 31 号，起始日不能晚于结束日");
                }
                configService.setConfig(KEY_WITHDRAW_MONTHLY_START, String.valueOf(start));
                configService.setConfig(KEY_WITHDRAW_MONTHLY_END, String.valueOf(end));
                configService.setConfig(KEY_WITHDRAW_ALLOWED_DAYS, "");
                return Result.success("提现日期设置已保存");
            } catch (NumberFormatException exception) {
                return Result.error("提现日期必须是数字");
            }
        }
        String normalizedDays;
        try {
            normalizedDays = normalizeWithdrawDays(allowedDays);
        } catch (IllegalArgumentException exception) {
            return Result.error(exception.getMessage());
        }
        configService.setConfig(KEY_WITHDRAW_ALLOWED_DAYS, normalizedDays);
        return Result.success("提现日期设置已保存");
    }

    /**
     * 获取提现可用状态（小程序调用）
     * 返回今天是否可以提现，以及允许提现的日期列表
     */
    @GetMapping("/withdraw-status")
    public Result<Object> getWithdrawStatus() {
        int monthlyStart = parseDay(configService.getConfig(KEY_WITHDRAW_MONTHLY_START, "1"), 1);
        int monthlyEnd = parseDay(configService.getConfig(KEY_WITHDRAW_MONTHLY_END, "31"), 31);
        if (monthlyStart <= monthlyEnd && (monthlyStart != 1 || monthlyEnd != 31)) {
            java.time.LocalDate todayDate = java.time.LocalDate.now();
            int today = todayDate.getDayOfMonth();
            int effectiveEnd = Math.min(monthlyEnd, todayDate.lengthOfMonth());
            boolean canWithdraw = monthlyStart <= effectiveEnd && today >= monthlyStart && today <= effectiveEnd;
            String rangeText = "每月" + monthlyStart + "号至" + monthlyEnd + "号";
            Map<String, Object> result = new HashMap<>();
            result.put("monthlyStartDay", monthlyStart); result.put("monthlyEndDay", monthlyEnd);
            result.put("allowedDaysText", rangeText); result.put("canWithdraw", canWithdraw);
            result.put("message", canWithdraw ? "今日可申请提现" : "今日暂不可申请，开放时间为" + rangeText);
            result.put("minWithdraw", 0.01D); result.put("processingTime", "1-3个工作日");
            return Result.success(result);
        }
        String allowedDays;
        try {
            allowedDays = normalizeWithdrawDays(configService.getConfig(KEY_WITHDRAW_ALLOWED_DAYS, ""));
        } catch (IllegalArgumentException exception) {
            allowedDays = "";
        }
        boolean unrestricted = allowedDays.isEmpty();
        int today = java.time.LocalDate.now().getDayOfWeek().getValue();
        boolean canWithdraw = unrestricted || java.util.Arrays.stream(allowedDays.split(","))
                .anyMatch(day -> !day.isBlank() && Integer.parseInt(day) == today);
        String allowedDaysText = unrestricted ? "每天" : formatWithdrawDays(allowedDays);

        Map<String, Object> result = new HashMap<>();
        result.put("allowedDays", allowedDays);
        result.put("allowedDaysText", allowedDaysText);
        result.put("canWithdraw", canWithdraw);
        result.put("message", canWithdraw
                ? "今日可申请提现"
                : "今日暂不可申请，允许提现日为" + allowedDaysText);
        result.put("minWithdraw", 0.01D);
        result.put("processingTime", "1-3个工作日");
        return Result.success(result);
    }

    private int parseDay(String value, int fallback) {
        try { int day = Integer.parseInt(value); return day >= 1 && day <= 31 ? day : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private String normalizeWithdrawDays(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        java.util.SortedSet<Integer> days = new java.util.TreeSet<>();
        for (String item : value.split(",")) {
            try {
                int day = Integer.parseInt(item.trim());
                if (day < 1 || day > 7) {
                    throw new IllegalArgumentException("提现日期只能选择周一至周日");
                }
                days.add(day);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("提现日期格式不正确");
            }
        }
        return days.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private String formatWithdrawDays(String allowedDays) {
        if (allowedDays.split(",").length == 7) {
            return "每天（周一至周日）";
        }
        String[] names = { "", "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
        return java.util.Arrays.stream(allowedDays.split(","))
                .filter(day -> !day.isBlank())
                .map(day -> names[Integer.parseInt(day)])
                .collect(java.util.stream.Collectors.joining("、"));
    }
}
