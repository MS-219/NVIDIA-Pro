package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.SystemConfig;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.ISystemConfigService;
import com.juxin.orin.service.InviteLevelConfigService;
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
    private static final String KEY_HOURLY_RATE = "earnings.hourlyRate";
    private static final String KEY_HASHRATE_PER_YUAN = "earnings.hashratePerYuan";
    private static final String KEY_MIN_WITHDRAW = "earnings.minWithdraw";
    private static final String KEY_WITHDRAW_FEE = "earnings.withdrawFee";
    private static final String KEY_EARNINGS_RATE = "invite.earningsRate";
    private static final String KEY_EARNINGS_CYCLE = "earnings.cycle";

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

    private static final String KEY_MAINTENANCE_MODE = "system.maintenanceMode";
    private static final String KEY_BANNER_LIST = "system.bannerList";
    private static final String KEY_WITHDRAW_ALLOWED_DAYS = "withdraw.allowedDays"; // 允许提现的星期几,如"1,4"表示周一和周四

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
        earnings.put("hourlyRate", Double.parseDouble(configService.getConfig(KEY_HOURLY_RATE, "2.4")));
        earnings.put("hashratePerYuan", Integer.parseInt(configService.getConfig(KEY_HASHRATE_PER_YUAN, "100")));
        earnings.put("minWithdraw", Double.parseDouble(configService.getConfig(KEY_MIN_WITHDRAW, "10")));
        earnings.put("withdrawFee", Double.parseDouble(configService.getConfig(KEY_WITHDRAW_FEE, "1")));
        earnings.put("cycle", Integer.parseInt(configService.getConfig(KEY_EARNINGS_CYCLE, "60")));
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
        String withdrawDays = configService.getConfig(KEY_WITHDRAW_ALLOWED_DAYS, "");
        settings.put("withdrawAllowedDays", withdrawDays);

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

        if (params.get("hourlyRate") != null) {
            configService.setConfig(KEY_HOURLY_RATE, params.get("hourlyRate").toString());
        }
        if (params.get("hashratePerYuan") != null) {
            configService.setConfig(KEY_HASHRATE_PER_YUAN, params.get("hashratePerYuan").toString());
        }
        if (params.get("minWithdraw") != null) {
            configService.setConfig(KEY_MIN_WITHDRAW, params.get("minWithdraw").toString());
        }
        if (params.get("withdrawFee") != null) {
            configService.setConfig(KEY_WITHDRAW_FEE, params.get("withdrawFee").toString());
        }
        if (params.get("cycle") != null) {
            configService.setConfig(KEY_EARNINGS_CYCLE, params.get("cycle").toString());
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
        config.put("minWithdraw", Double.parseDouble(configService.getConfig(KEY_MIN_WITHDRAW, "10")));
        config.put("withdrawFee", Double.parseDouble(configService.getConfig(KEY_WITHDRAW_FEE, "1")));
        config.put("hourlyRate", Double.parseDouble(configService.getConfig(KEY_HOURLY_RATE, "2.4")));
        return Result.success(config);
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
        configService.setConfig(KEY_WITHDRAW_ALLOWED_DAYS, allowedDays);
        return Result.success("提现日期限制设置已保存");
    }

    /**
     * 获取提现可用状态（小程序调用）
     * 返回今天是否可以提现，以及允许提现的日期列表
     */
    @GetMapping("/withdraw-status")
    public Result<Object> getWithdrawStatus() {
        String allowedDays = configService.getConfig(KEY_WITHDRAW_ALLOWED_DAYS, "");

        Map<String, Object> result = new HashMap<>();
        result.put("allowedDays", allowedDays);

        // 如果没有配置限制，则任何时候都可以提现
        if (allowedDays == null || allowedDays.trim().isEmpty()) {
            result.put("canWithdraw", true);
            result.put("message", "");
            return Result.success(result);
        }

        // 检查今天是否在允许的日期内
        // Java: 1=周一, 7=周日 (使用 DayOfWeek)
        int todayDayOfWeek = java.time.LocalDate.now().getDayOfWeek().getValue();
        String[] days = allowedDays.split(",");
        boolean canWithdraw = false;

        for (String day : days) {
            try {
                if (Integer.parseInt(day.trim()) == todayDayOfWeek) {
                    canWithdraw = true;
                    break;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        result.put("canWithdraw", canWithdraw);

        if (!canWithdraw) {
            // 生成提示信息
            String[] dayNames = { "", "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
            StringBuilder sb = new StringBuilder("提现日为每周");
            for (int i = 0; i < days.length; i++) {
                try {
                    int d = Integer.parseInt(days[i].trim());
                    if (d >= 1 && d <= 7) {
                        if (i > 0)
                            sb.append("、");
                        sb.append(dayNames[d]);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            result.put("message", sb.toString());
        } else {
            result.put("message", "");
        }

        return Result.success(result);
    }
}
