package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.entity.*;
import com.juxin.orin.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放 API 控制器 (面向商户)
 */
@RestController
@RequestMapping("/api/open")
public class OpenApiController {

    @Autowired
    private IApiMerchantService apiMerchantService;

    @Autowired
    private IAppUserService appUserService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IDeviceEarningsService deviceEarningsService;

    @Autowired
    private IWithdrawService withdrawService;

    private Result<ApiMerchant> validateMerchant(String appId, String timestamp, String data, String sign) {
        // 1. 时间戳校验 (防止重放攻击，允许 5 分钟误差)
        try {
            long requestTime = Long.parseLong(timestamp);
            long now = System.currentTimeMillis();
            if (Math.abs(now - requestTime) > 5 * 60 * 1000) {
                return Result.error("请求已过期，请检查系统时间");
            }
        } catch (Exception e) {
            return Result.error("无效的时间戳");
        }

        // 2. 验证商户是否存在
        ApiMerchant merchant = apiMerchantService.getByAppId(appId);
        if (merchant == null) {
            return Result.error("认证失败");
        }

        // 3. 验证签名 (先验签，防止通过不同错误信息探测商户状态)
        String raw = appId + (data != null ? data : "") + timestamp + merchant.getAppSecret();
        String expectedSign = DigestUtils.md5DigestAsHex(java.util.Objects.requireNonNull(raw.getBytes(StandardCharsets.UTF_8)));
        if (!expectedSign.equalsIgnoreCase(sign)) {
            return Result.error("认证失败");
        }

        // 4. 验证商户是否已禁用
        if (merchant.getStatus() == null || merchant.getStatus() != 1) {
            return Result.error("商户账号已被禁用");
        }

        // 5. 验证商户是否已过期
        if (merchant.getExpireTime() != null && merchant.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
            return Result.error("商户服务已到期");
        }

        return Result.success(merchant);
    }

    private Result<?> validatePermission(ApiMerchant merchant, String permission) {
        if (merchant.getPermissions() == null || merchant.getPermissions().isEmpty()) {
            return Result.error("该商户未开通任何功能权限");
        }
        List<String> allowedPermissions = Arrays.asList(merchant.getPermissions().split(","));
        if (!allowedPermissions.contains(permission)) {
            return Result.error("商户无权调用该功能: " + permission);
        }
        return null;
    }

    /**
     * 同步外部用户
     */
    @PostMapping("/user/sync")
    public Result<Map<String, Object>> syncUser(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestBody Map<String, String> params) {

        String externalUserId = params.get("externalUserId");
        if (externalUserId == null)
            return Result.error("externalUserId 不能为空");

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, externalUserId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "user-sync");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        AppUser user = appUserService.syncExternalUser(merchant.getId(), externalUserId, params.get("nickname"),
                params.get("avatarUrl"));
        return Result.success(toOpenUser(user));
    }

    /**
     * 绑定设备
     */
    @PostMapping("/device/bind")
    public Result<String> bindDevice(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestBody Map<String, String> params) {

        String externalUserId = params.get("externalUserId");
        String sn = params.get("sn");
        String bindCode = params.get("bindCode");

        if (externalUserId == null)
            return Result.error("externalUserId 不能为空");
        if (sn == null && bindCode == null)
            return Result.error("sn 或 bindCode 不能为空");

        // 签名校验证书
        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp,
                externalUserId + (sn != null ? sn : bindCode), sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "device-bind");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        Long userId = merchant.getBindUserId();
        if (userId == null) {
            return Result.error("商户未绑定平台账户，无法分配设备");
        }

        // 小程序侧设备统一绑定到商户主账号，merchantId 继续保留商户归属
        boolean success = deviceService.bindDevice(sn, bindCode, userId, merchant.getId());
        return success ? Result.success("绑定成功") : Result.error("绑定失败：设备不存在或已被绑定");
    }

    /**
     * 解绑设备
     */
    @PostMapping("/device/unbind")
    public Result<String> unbindDevice(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestBody Map<String, String> params) {

        String externalUserId = params.get("externalUserId");
        String sn = params.get("sn");

        if (externalUserId == null || sn == null)
            return Result.error("externalUserId 和 sn 不能为空");

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, externalUserId + sn, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "device-bind");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        // 解绑口径与绑定保持一致，只校验设备是否属于当前商户
        Device device = deviceService.lambdaQuery()
                .eq(Device::getSn, sn)
                .eq(Device::getMerchantId, merchant.getId())
                .one();
        if (device == null)
            return Result.error("设备不属于该商户或不存在");

        boolean success = deviceService.lambdaUpdate()
                .eq(Device::getSn, sn)
                .set(Device::getUserId, null)
                .set(Device::getBindTime, null)
                .update();

        return success ? Result.success("解绑成功") : Result.error("解绑失败");
    }

    /**
     * 获用户设备列表
     */
    @GetMapping("/device/list")
    public Result<List<Map<String, Object>>> listDevices(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam String externalUserId) {

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, externalUserId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "device-list");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        // 保留 externalUserId 参数用于兼容现有签名，但设备列表按商户归属返回
        List<Device> list = deviceService.lambdaQuery()
                .eq(Device::getMerchantId, merchant.getId())
                .and(w -> w.ne(Device::getType, 1).or().isNull(Device::getType))
                .list();

        return Result.success(list.stream()
                .map(this::toOpenDevice)
                .collect(java.util.stream.Collectors.toList()));
    }

    // ==================== 收益相关接口 ====================

    /**
     * 查询用户收益汇总
     */
    @GetMapping("/earnings/summary")
    public Result<Map<String, Object>> getEarningsSummary(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam String externalUserId) {

        if (externalUserId == null || externalUserId.isEmpty())
            return Result.error("externalUserId 不能为空");

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, externalUserId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "earnings-query");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        AppUser user = appUserService.syncExternalUser(merchant.getId(), externalUserId, null, null);

        // 获取收益统计
        Map<String, BigDecimal> earnings = deviceEarningsService.getUserEarnings(user.getId());
        // 获取钱包信息
        Map<String, BigDecimal> wallet = withdrawService.getWalletInfo(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("totalEarnings", earnings.get("total")); // 累计总收益
        result.put("yesterdayEarnings", earnings.get("yesterday")); // 昨日收益
        result.put("balance", user.getBalance()); // 当前余额
        result.put("withdrawable", wallet.get("withdrawable")); // 可提现金额
        result.put("pendingWithdraw", wallet.get("pending")); // 待审核提现金额

        return Result.success(result);
    }

    /**
     * 查询用户收益明细（含设备SN和精确时间戳）
     */
    @GetMapping("/earnings/list")
    public Result<List<Map<String, Object>>> getEarningsList(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam String externalUserId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        if (externalUserId == null || externalUserId.isEmpty())
            return Result.error("externalUserId 不能为空");

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, externalUserId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "earnings-query");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        AppUser user = appUserService.syncExternalUser(merchant.getId(), externalUserId, null, null);

        // 查询收益明细
        List<DeviceEarnings> list = deviceEarningsService.lambdaQuery()
                .eq(DeviceEarnings::getUserId, user.getId())
                .orderByDesc(DeviceEarnings::getCreateTime)
                .last("LIMIT " + (page - 1) * size + ", " + size)
                .list();

        // 收集需要查询 SN 的设备 ID
        List<Long> deviceIds = list.stream()
                .map(DeviceEarnings::getDeviceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        Map<Long, String> deviceSnMap = new HashMap<>();
        if (!deviceIds.isEmpty()) {
            List<Device> devices = deviceService.lambdaQuery()
                    .select(Device::getId, Device::getSn)
                    .in(Device::getId, deviceIds)
                    .list();
            deviceSnMap = devices.stream()
                    .collect(java.util.stream.Collectors.toMap(Device::getId, Device::getSn, (a, b) -> a));
        }

        // 组装返回数据，带上 deviceSn
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (DeviceEarnings e : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", e.getId());
            item.put("deviceSn", deviceSnMap.getOrDefault(e.getDeviceId(), "UNKNOWN"));
            item.put("amount", e.getAmount());
            item.put("date", e.getDate() != null ? e.getDate().toString() : null);
            item.put("createTime", e.getCreateTime() != null
                    ? e.getCreateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : null);
            records.add(item);
        }

        return Result.success(records);
    }

    /**
     * 查询用户提现记录
     */
    @GetMapping("/withdraw/list")
    public Result<List<Withdraw>> getWithdrawList(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam String externalUserId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        if (externalUserId == null || externalUserId.isEmpty())
            return Result.error("externalUserId 不能为空");

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, externalUserId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "earnings-query");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        AppUser user = appUserService.syncExternalUser(merchant.getId(), externalUserId, null, null);

        // 查询提现记录
        Page<Withdraw> pageResult = withdrawService.getUserWithdrawList(user.getId(), page, size);

        return Result.success(pageResult.getRecords());
    }

    // ==================== 商户管理类接口 (代理商视角) ====================

    /**
     * 获取商户概览统计 (代理商视角)
     */
    @GetMapping("/merchant/summary")
    public Result<Map<String, Object>> getMerchantSummary(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign) {

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, appId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();

        // 1. 获取名下总用户数
        long totalUsers = appUserService.lambdaQuery().eq(AppUser::getMerchantId, merchant.getId()).count();

        // 2. 获取名下总设备数
        // 先拿到用户ID列表
        List<AppUser> users = appUserService.lambdaQuery()
                .select(AppUser::getId)
                .eq(AppUser::getMerchantId, merchant.getId())
                .list();

        long totalDevices = 0;
        long onlineDevices = 0;
        BigDecimal totalUserEarnings = BigDecimal.ZERO;

        if (!users.isEmpty()) {
            List<Long> userIds = users.stream().map(AppUser::getId).collect(java.util.stream.Collectors.toList());
            List<Device> visibleDevices = deviceService.lambdaQuery()
                    .select(Device::getId, Device::getStatus)
                    .in(Device::getUserId, userIds)
                    .and(w -> w.ne(Device::getType, 1).or().isNull(Device::getType))
                    .list();
            totalDevices = visibleDevices.size();
            onlineDevices = visibleDevices.stream()
                    .filter(d -> d.getStatus() != null && d.getStatus() == 1)
                    .count();

            // 获取下属用户产生的总收益
            List<Long> visibleDeviceIds = visibleDevices.stream()
                    .map(Device::getId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
            if (!visibleDeviceIds.isEmpty()) {
                totalUserEarnings = deviceEarningsService.lambdaQuery()
                        .in(DeviceEarnings::getDeviceId, visibleDeviceIds)
                        .list()
                        .stream()
                        .map(DeviceEarnings::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }

        Map<String, Object> result = new HashMap<>();
        BigDecimal merchantBalance = merchant.getBalance() != null ? merchant.getBalance() : BigDecimal.ZERO;
        if (merchant.getBindUserId() != null) {
            AppUser bindUser = appUserService.getById(merchant.getBindUserId());
            if (bindUser != null) {
                merchantBalance = bindUser.getBalance() != null ? bindUser.getBalance() : BigDecimal.ZERO;
            }
        }
        result.put("merchantName", merchant.getMerchantName());
        result.put("totalUsers", totalUsers);
        result.put("totalDevices", totalDevices);
        result.put("onlineDevices", onlineDevices);
        result.put("totalUserEarnings", totalUserEarnings);
        result.put("balance", merchantBalance);
        result.put("level", merchant.getLevel() != null ? merchant.getLevel() : 0);
        result.put("expireTime", merchant.getExpireTime());

        return Result.success(result);
    }

    /**
     * 分页获取商户下属用户列表
     */
    @GetMapping("/merchant/user/list")
    public Result<List<Map<String, Object>>> getMerchantUserList(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, appId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();

        Page<AppUser> pageResult = appUserService.lambdaQuery()
                .eq(AppUser::getMerchantId, merchant.getId())
                .orderByDesc(AppUser::getCreateTime)
                .page(new Page<>(page, size));

        return Result.success(pageResult.getRecords().stream()
                .map(this::toOpenUser)
                .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * 获取商户下属所有设备列表
     */
    @GetMapping("/merchant/device/list")
    public Result<List<Map<String, Object>>> getMerchantDeviceList(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, appId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();

        // 1. 直接查询归属于该商户的设备
        Page<Device> devicePage = deviceService.lambdaQuery()
                .eq(Device::getMerchantId, merchant.getId())
                .and(w -> w.ne(Device::getType, 1).or().isNull(Device::getType))
                .orderByDesc(Device::getCreateTime)
                .page(new Page<>(page, size));

        if (devicePage.getRecords().isEmpty()) {
            return Result.success(new java.util.ArrayList<>());
        }

        // 2. 获取去重后的用户 ID 以加载用户信息
        List<Long> userIds = devicePage.getRecords().stream()
                .map(Device::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        Map<Long, AppUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<AppUser> users = appUserService.listByIds(userIds);
            userMap = users.stream().collect(java.util.stream.Collectors.toMap(AppUser::getId, u -> u));
        }

        // 3. 组装数据
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (Device device : devicePage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("sn", device.getSn());
            item.put("bindCode", device.getBindCode());
            item.put("name", device.getName());
            item.put("status", device.getStatus() != null && device.getStatus() == 1 ? 1 : 0);
            item.put("lastHeartbeat", device.getLastHeartbeatTime());

            AppUser owner = userMap.get(device.getUserId());
            if (owner != null) {
                item.put("externalUserId", owner.getExternalUserId());
                item.put("nickname", owner.getNickname());
            }
            records.add(item);
        }

        return Result.success(records);
    }

    /**
     * 获取商户名下设备收益（按设备统计）
     */
    @GetMapping("/merchant/device/earnings")
    public Result<List<Map<String, Object>>> getMerchantDeviceEarnings(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, appId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();

        Page<Device> devicePage = deviceService.lambdaQuery()
                .eq(Device::getMerchantId, merchant.getId())
                .and(w -> w.ne(Device::getType, 1).or().isNull(Device::getType))
                .orderByDesc(Device::getCreateTime)
                .page(new Page<>(page, size));

        if (devicePage.getRecords().isEmpty()) {
            return Result.success(new java.util.ArrayList<>());
        }

        List<Long> deviceIds = devicePage.getRecords().stream()
                .map(Device::getId)
                .collect(java.util.stream.Collectors.toList());

        List<DeviceEarnings> earningsList = deviceEarningsService.lambdaQuery()
                .in(DeviceEarnings::getDeviceId, deviceIds)
                .list();

        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        Map<Long, BigDecimal> totalMap = new HashMap<>();
        Map<Long, BigDecimal> yesterdayMap = new HashMap<>();

        for (DeviceEarnings e : earningsList) {
            if (e.getDeviceId() == null || e.getAmount() == null)
                continue;
            totalMap.merge(e.getDeviceId(), e.getAmount(), BigDecimal::add);
            if (e.getDate() != null && e.getDate().equals(yesterday)) {
                yesterdayMap.merge(e.getDeviceId(), e.getAmount(), BigDecimal::add);
            }
        }

        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (Device device : devicePage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("sn", device.getSn());
            item.put("name", device.getName());
            item.put("status", device.getStatus() != null && device.getStatus() == 1 ? 1 : 0);
            item.put("lastHeartbeat", device.getLastHeartbeatTime());
            item.put("totalEarnings", totalMap.getOrDefault(device.getId(), BigDecimal.ZERO));
            item.put("yesterdayEarnings", yesterdayMap.getOrDefault(device.getId(), BigDecimal.ZERO));
            records.add(item);
        }

        return Result.success(records);
    }

    /**
     * 获取商户名下全部设备的小时收益明细（平铺列表）
     */
    @GetMapping("/merchant/earnings/hourly")
    public Result<Map<String, Object>> getMerchantHourlyEarnings(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, appId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "earnings-query");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        java.time.LocalDateTime start = null;
        java.time.LocalDateTime end = null;
        try {
            if (startTime != null && !startTime.isEmpty()) {
                start = java.time.LocalDateTime.parse(startTime,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            if (endTime != null && !endTime.isEmpty()) {
                end = java.time.LocalDateTime.parse(endTime,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception e) {
            return Result.error("时间格式错误，请使用 yyyy-MM-dd HH:mm:ss");
        }

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : size;

        List<Device> merchantDevices = deviceService.lambdaQuery()
                .select(Device::getId, Device::getSn, Device::getUserId)
                .eq(Device::getMerchantId, merchant.getId())
                .and(w -> w.ne(Device::getType, 1).or().isNull(Device::getType))
                .list();

        if (merchantDevices.isEmpty()) {
            return Result.success(buildPagedResult(page, size, 0L, java.util.Collections.emptyList()));
        }

        List<Long> deviceIds = merchantDevices.stream()
                .map(Device::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (deviceIds.isEmpty()) {
            return Result.success(buildPagedResult(page, size, 0L, java.util.Collections.emptyList()));
        }

        Map<Long, String> deviceSnMap = merchantDevices.stream()
                .filter(d -> d.getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        Device::getId,
                        d -> d.getSn() != null ? d.getSn() : "UNKNOWN",
                        (a, b) -> a));

        List<Long> userIds = merchantDevices.stream()
                .map(Device::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        Map<Long, String> userExtIdMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<AppUser> users = appUserService.lambdaQuery()
                    .select(AppUser::getId, AppUser::getExternalUserId)
                    .in(AppUser::getId, userIds)
                    .list();
            userExtIdMap = users.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            AppUser::getId,
                            u -> u.getExternalUserId() != null ? u.getExternalUserId() : "",
                            (a, b) -> a));
        }

        var countQuery = deviceEarningsService.lambdaQuery()
                .in(DeviceEarnings::getDeviceId, deviceIds);
        if (start != null) {
            countQuery.ge(DeviceEarnings::getCreateTime, start);
        }
        if (end != null) {
            countQuery.le(DeviceEarnings::getCreateTime, end);
        }
        long total = countQuery.count();

        var earningsQuery = deviceEarningsService.lambdaQuery()
                .in(DeviceEarnings::getDeviceId, deviceIds);
        if (start != null) {
            earningsQuery.ge(DeviceEarnings::getCreateTime, start);
        }
        if (end != null) {
            earningsQuery.le(DeviceEarnings::getCreateTime, end);
        }

        List<DeviceEarnings> list = earningsQuery
                .orderByDesc(DeviceEarnings::getCreateTime)
                .last("LIMIT " + (safePage - 1) * safeSize + ", " + safeSize)
                .list();

        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (DeviceEarnings e : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", e.getId());
            item.put("deviceSn", deviceSnMap.getOrDefault(e.getDeviceId(), "UNKNOWN"));
            item.put("externalUserId", userExtIdMap.getOrDefault(e.getUserId(), ""));
            item.put("amount", e.getAmount());
            item.put("date", e.getDate() != null ? e.getDate().toString() : null);
            item.put("createTime", e.getCreateTime() != null
                    ? e.getCreateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : null);
            records.add(item);
        }

        return Result.success(buildPagedResult(page, size, total, records));
    }

    /**
     * 获取商户按日收益汇总（含设备维度明细）
     */
    @GetMapping("/merchant/earnings/daily")
    public Result<Map<String, Object>> getMerchantDailyEarnings(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "30") Integer size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, appId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "earnings-query");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        java.time.LocalDate start = null;
        java.time.LocalDate end = null;
        try {
            if (startDate != null && !startDate.isEmpty()) {
                start = java.time.LocalDate.parse(startDate);
            }
            if (endDate != null && !endDate.isEmpty()) {
                end = java.time.LocalDate.parse(endDate);
            }
        } catch (Exception e) {
            return Result.error("日期格式错误，请使用 yyyy-MM-dd");
        }

        List<Device> devices = deviceService.lambdaQuery()
                .select(Device::getId, Device::getSn, Device::getUserId)
                .eq(Device::getMerchantId, merchant.getId())
                .and(w -> w.ne(Device::getType, 1).or().isNull(Device::getType))
                .list();

        if (devices.isEmpty()) {
            return Result.success(buildPagedSummaryResult(page, size, java.util.Collections.emptyList()));
        }

        List<Long> deviceIds = devices.stream()
                .map(Device::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (deviceIds.isEmpty()) {
            return Result.success(buildPagedSummaryResult(page, size, java.util.Collections.emptyList()));
        }

        Map<Long, String> deviceSnMap = devices.stream()
                .filter(d -> d.getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        Device::getId,
                        d -> d.getSn() != null ? d.getSn() : "UNKNOWN",
                        (a, b) -> a));

        List<Long> userIds = devices.stream()
                .map(Device::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        Map<Long, String> userExtIdMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<AppUser> users = appUserService.lambdaQuery()
                    .select(AppUser::getId, AppUser::getExternalUserId)
                    .in(AppUser::getId, userIds)
                    .list();
            userExtIdMap = users.stream()
                    .collect(java.util.stream.Collectors.toMap(AppUser::getId,
                            u -> u.getExternalUserId() != null ? u.getExternalUserId() : "",
                            (a, b) -> a));
        }

        // 查询收益明细
        var earningsQuery = deviceEarningsService.lambdaQuery().in(DeviceEarnings::getDeviceId, deviceIds);
        if (start != null) {
            earningsQuery.ge(DeviceEarnings::getDate, start);
        }
        if (end != null) {
            earningsQuery.le(DeviceEarnings::getDate, end);
        }
        List<DeviceEarnings> earningsList = earningsQuery.orderByDesc(DeviceEarnings::getDate).list();

        // 按 日期+设备 维度聚合，同时汇总每日总计
        // key = "date|deviceId"
        java.util.Map<String, java.math.BigDecimal> detailMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, Long> detailDeviceMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, Long> detailUserMap = new java.util.LinkedHashMap<>();
        java.util.Map<java.time.LocalDate, java.math.BigDecimal> dailyTotalMap = new java.util.TreeMap<>(
                java.util.Comparator.reverseOrder());

        for (DeviceEarnings earnings : earningsList) {
            if (earnings.getDate() == null || earnings.getAmount() == null) {
                continue;
            }
            // 每日总计
            dailyTotalMap.merge(earnings.getDate(), earnings.getAmount(), java.math.BigDecimal::add);

            // 按设备明细
            String key = earnings.getDate().toString() + "|" + earnings.getDeviceId();
            detailMap.merge(key, earnings.getAmount(), java.math.BigDecimal::add);
            detailDeviceMap.putIfAbsent(key, earnings.getDeviceId());
            detailUserMap.putIfAbsent(key, earnings.getUserId());
        }

        // 组装返回数据：每日一条汇总 + 对应的设备明细列表
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (java.util.Map.Entry<java.time.LocalDate, java.math.BigDecimal> dayEntry : dailyTotalMap.entrySet()) {
            String dateStr = dayEntry.getKey().toString();
            Map<String, Object> dayItem = new HashMap<>();
            dayItem.put("date", dateStr);
            dayItem.put("dailyTotal", dayEntry.getValue());

            // 该日下的设备明细
            List<Map<String, Object>> details = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, java.math.BigDecimal> detailEntry : detailMap.entrySet()) {
                if (!detailEntry.getKey().startsWith(dateStr + "|")) {
                    continue;
                }
                Long deviceId = detailDeviceMap.get(detailEntry.getKey());
                Long userId = detailUserMap.get(detailEntry.getKey());
                Map<String, Object> detail = new HashMap<>();
                detail.put("deviceSn", deviceSnMap.getOrDefault(deviceId, "UNKNOWN"));
                detail.put("externalUserId", userExtIdMap.getOrDefault(userId, ""));
                detail.put("earnings", detailEntry.getValue());
                details.add(detail);
            }
            dayItem.put("devices", details);
            records.add(dayItem);
        }

        return Result.success(buildPagedSummaryResult(page, size, records));
    }

    /**
     * 获取商户按月收益汇总（含设备维度明细）
     */
    @GetMapping("/merchant/earnings/monthly")
    public Result<Map<String, Object>> getMerchantMonthlyEarnings(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "12") Integer size,
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth) {

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, appId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();
        Result<?> permCheck = validatePermission(merchant, "earnings-query");
        if (permCheck != null)
            return Result.error(permCheck.getMsg());

        java.time.YearMonth start = null;
        java.time.YearMonth end = null;
        try {
            if (startMonth != null && !startMonth.isEmpty()) {
                start = java.time.YearMonth.parse(startMonth);
            }
            if (endMonth != null && !endMonth.isEmpty()) {
                end = java.time.YearMonth.parse(endMonth);
            }
        } catch (Exception e) {
            return Result.error("月份格式错误，请使用 yyyy-MM");
        }

        List<Device> devices = deviceService.lambdaQuery()
                .select(Device::getId, Device::getSn, Device::getUserId)
                .eq(Device::getMerchantId, merchant.getId())
                .and(w -> w.ne(Device::getType, 1).or().isNull(Device::getType))
                .list();

        if (devices.isEmpty()) {
            return Result.success(buildPagedSummaryResult(page, size, java.util.Collections.emptyList()));
        }

        List<Long> deviceIds = devices.stream()
                .map(Device::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (deviceIds.isEmpty()) {
            return Result.success(buildPagedSummaryResult(page, size, java.util.Collections.emptyList()));
        }

        Map<Long, String> deviceSnMap = devices.stream()
                .filter(d -> d.getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        Device::getId,
                        d -> d.getSn() != null ? d.getSn() : "UNKNOWN",
                        (a, b) -> a));

        List<Long> userIds = devices.stream()
                .map(Device::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        Map<Long, String> userExtIdMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<AppUser> users = appUserService.lambdaQuery()
                    .select(AppUser::getId, AppUser::getExternalUserId)
                    .in(AppUser::getId, userIds)
                    .list();
            userExtIdMap = users.stream()
                    .collect(java.util.stream.Collectors.toMap(AppUser::getId,
                            u -> u.getExternalUserId() != null ? u.getExternalUserId() : "",
                            (a, b) -> a));
        }

        List<DeviceEarnings> earningsList = deviceEarningsService.lambdaQuery()
                .in(DeviceEarnings::getDeviceId, deviceIds)
                .list();

        // 按 月份+设备 维度聚合，同时汇总每月总计
        java.util.Map<String, java.math.BigDecimal> monthlyTotalMap = new java.util.TreeMap<>(
                java.util.Comparator.reverseOrder());
        // key = "yyyy-MM|deviceId"
        java.util.Map<String, java.math.BigDecimal> detailMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, Long> detailDeviceMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, Long> detailUserMap = new java.util.LinkedHashMap<>();

        for (DeviceEarnings earnings : earningsList) {
            if (earnings.getDate() == null || earnings.getAmount() == null) {
                continue;
            }
            java.time.YearMonth currentMonth = java.time.YearMonth.from(earnings.getDate());
            if (start != null && currentMonth.isBefore(start)) {
                continue;
            }
            if (end != null && currentMonth.isAfter(end)) {
                continue;
            }

            String monthStr = currentMonth.toString();
            // 每月总计
            monthlyTotalMap.merge(monthStr, earnings.getAmount(), java.math.BigDecimal::add);

            // 按设备明细
            String key = monthStr + "|" + earnings.getDeviceId();
            detailMap.merge(key, earnings.getAmount(), java.math.BigDecimal::add);
            detailDeviceMap.putIfAbsent(key, earnings.getDeviceId());
            detailUserMap.putIfAbsent(key, earnings.getUserId());
        }

        // 组装返回数据：每月一条汇总 + 对应的设备明细列表
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.math.BigDecimal> monthEntry : monthlyTotalMap.entrySet()) {
            String monthStr = monthEntry.getKey();
            Map<String, Object> monthItem = new HashMap<>();
            monthItem.put("month", monthStr);
            monthItem.put("monthlyTotal", monthEntry.getValue());

            // 该月下的设备明细
            List<Map<String, Object>> details = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, java.math.BigDecimal> detailEntry : detailMap.entrySet()) {
                if (!detailEntry.getKey().startsWith(monthStr + "|")) {
                    continue;
                }
                Long deviceId = detailDeviceMap.get(detailEntry.getKey());
                Long userId = detailUserMap.get(detailEntry.getKey());
                Map<String, Object> detail = new HashMap<>();
                detail.put("deviceSn", deviceSnMap.getOrDefault(deviceId, "UNKNOWN"));
                detail.put("externalUserId", userExtIdMap.getOrDefault(userId, ""));
                detail.put("earnings", detailEntry.getValue());
                details.add(detail);
            }
            monthItem.put("devices", details);
            records.add(monthItem);
        }

        return Result.success(buildPagedSummaryResult(page, size, records));
    }

    /**
     * 获取商户发展数据 (近30天趋势)
     */
    @GetMapping("/merchant/stats/trend")
    public Result<Map<String, Object>> getMerchantTrend(
            @RequestHeader("App-Id") String appId,
            @RequestHeader("Timestamp") String timestamp,
            @RequestHeader("Sign") String sign) {

        Result<ApiMerchant> merchantResult = validateMerchant(appId, timestamp, appId, sign);
        if (merchantResult.getCode() != 200)
            return Result.error(merchantResult.getMsg());

        ApiMerchant merchant = merchantResult.getData();

        // 简单返回最近 30 天的每日新增用户数
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Long> userGrowth = new java.util.ArrayList<>();
        java.util.List<Long> deviceGrowth = new java.util.ArrayList<>();
        java.util.List<BigDecimal> earningsGrowth = new java.util.ArrayList<>();

        // 性能优化：一次性查出所有下属用户，然后在内存中按日期统计
        List<AppUser> allMerchantUsers = appUserService.lambdaQuery()
                .select(AppUser::getId, AppUser::getCreateTime)
                .eq(AppUser::getMerchantId, merchant.getId())
                .list();

        List<Long> userIds = allMerchantUsers.stream().map(AppUser::getId)
                .collect(java.util.stream.Collectors.toList());
        List<Device> allMerchantDevices = new java.util.ArrayList<>();
        List<DeviceEarnings> allUserEarnings = new java.util.ArrayList<>();
        if (!userIds.isEmpty()) {
            allMerchantDevices = deviceService.lambdaQuery()
                    .select(Device::getId, Device::getCreateTime)
                    .in(Device::getUserId, userIds)
                    .and(w -> w.ne(Device::getType, 1).or().isNull(Device::getType))
                    .list();

            List<Long> visibleDeviceIds = allMerchantDevices.stream()
                    .map(Device::getId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
            if (!visibleDeviceIds.isEmpty()) {
                allUserEarnings = deviceEarningsService.lambdaQuery()
                        .select(DeviceEarnings::getAmount, DeviceEarnings::getCreateTime)
                        .in(DeviceEarnings::getDeviceId, visibleDeviceIds)
                        .list();
            }
        }

        for (int i = 29; i >= 0; i--) {
            java.time.LocalDate date = today.minusDays(i);
            labels.add(date.toString());

            long uCount = allMerchantUsers.stream()
                    .filter(u -> u.getCreateTime() != null && u.getCreateTime().toLocalDate().equals(date))
                    .count();
            userGrowth.add(uCount);

            long dCount = allMerchantDevices.stream()
                    .filter(d -> d.getCreateTime() != null && d.getCreateTime().toLocalDate().equals(date))
                    .count();
            deviceGrowth.add(dCount);

            BigDecimal eAmount = allUserEarnings.stream()
                    .filter(e -> e.getCreateTime() != null && e.getCreateTime().toLocalDate().equals(date))
                    .map(DeviceEarnings::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            earningsGrowth.add(eAmount);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("labels", labels);
        data.put("userGrowth", userGrowth);
        data.put("deviceGrowth", deviceGrowth);
        data.put("earningsGrowth", earningsGrowth);

        return Result.success(data);
    }

    private Map<String, Object> buildPagedSummaryResult(Integer page, Integer size, List<Map<String, Object>> records) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : size;
        int total = records.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);

        Map<String, Object> result = new HashMap<>();
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("total", total);
        result.put("records", records.subList(fromIndex, toIndex));
        return result;
    }

    private Map<String, Object> buildPagedResult(Integer page, Integer size, long total, List<Map<String, Object>> records) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : size;

        Map<String, Object> result = new HashMap<>();
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("total", total);
        result.put("records", records);
        return result;
    }

    private Map<String, Object> toOpenUser(AppUser user) {
        Map<String, Object> item = new HashMap<>();
        if (user == null) {
            return item;
        }
        item.put("externalUserId", user.getExternalUserId());
        item.put("nickname", user.getNickname());
        item.put("avatarUrl", user.getAvatarUrl());
        item.put("level", user.getLevel() != null ? user.getLevel() : 0);
        item.put("createTime", user.getCreateTime());
        return item;
    }

    private Map<String, Object> toOpenDevice(Device device) {
        Map<String, Object> item = new HashMap<>();
        if (device == null) {
            return item;
        }
        item.put("sn", device.getSn());
        item.put("bindCode", device.getBindCode());
        item.put("name", device.getName());
        item.put("status", device.getStatus() != null && device.getStatus() == 1 ? 1 : 0);
        item.put("lastHeartbeat", device.getLastHeartbeatTime());
        item.put("agentVersion", device.getAgentVersion());
        item.put("location", device.getLocation());
        item.put("carrier", device.getCarrier());
        return item;
    }
}
