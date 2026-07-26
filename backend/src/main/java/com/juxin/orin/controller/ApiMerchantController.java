package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.ApiMerchant;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IApiMerchantService;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.util.JwtUtil;
import com.juxin.orin.dto.DeviceEarningsDailyDto;
import com.juxin.orin.mapper.DeviceEarningsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 接口商户管理控制器 (后台管理)
 */
@RestController
@RequestMapping("/api/admin/api-merchants")
public class ApiMerchantController {

    @Autowired
    private IApiMerchantService apiMerchantService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private DeviceEarningsMapper deviceEarningsMapper;

    @Autowired
    private IAppUserService appUserService;

    private Result<?> checkAdmin(String token) {
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限访问此接口");
        }
        return null;
    }

    /**
     * 获取商户列表 (分页)
     */
    @GetMapping("/list")
    public Result<?> getMerchantList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null)
            return check;

        Page<ApiMerchant> merchantPage = apiMerchantService.page(new Page<>(page, size));
        // 为每个商户统计设备总数
        for (ApiMerchant merchant : merchantPage.getRecords()) {
            long count = deviceService.lambdaQuery().eq(Device::getMerchantId, merchant.getId()).count();
            merchant.setDeviceCount(count);
            fillBindUserInfo(merchant);
        }
        return Result.success(merchantPage);
    }

    /**
     * 新增商户
     */
    @PostMapping("/add")
    public Result<String> addMerchant(
            @RequestBody ApiMerchant merchant,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null)
            return Result.error(check.getMsg());

        // 自动生成 appId 和 appSecret
        merchant.setAppId("LD" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        merchant.setAppSecret(UUID.randomUUID().toString().replace("-", ""));

        boolean success = apiMerchantService.save(merchant);
        return success ? Result.success("添加成功") : Result.error("添加失败");
    }

    /**
     * 更新商户信息
     */
    @PutMapping("/update")
    public Result<String> updateMerchant(
            @RequestBody ApiMerchant merchant,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null)
            return Result.error(check.getMsg());

        if (merchant.getId() == null) {
            return Result.error("ID不能为空");
        }
        boolean success = apiMerchantService.updateById(merchant);
        return success ? Result.success("更新成功") : Result.error("更新失败");
    }

    /**
     * 绑定商户资金账号
     */
    @PostMapping("/{id}/bind-user")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> bindUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null) {
            return Result.error(check.getMsg());
        }

        ApiMerchant merchant = apiMerchantService.getById(id);
        if (merchant == null) {
            return Result.error("商户不存在");
        }

        Long bindUserId = null;
        if (params.get("bindUserId") != null && !params.get("bindUserId").toString().isEmpty()) {
            bindUserId = Long.valueOf(params.get("bindUserId").toString());
            AppUser user = appUserService.getById(bindUserId);
            if (user == null) {
                return Result.error("绑定账号不存在");
            }
        }

        boolean success = apiMerchantService.lambdaUpdate()
                .eq(ApiMerchant::getId, id)
                .set(ApiMerchant::getBindUserId, bindUserId)
                .update();
        if (!success) {
            return Result.error("绑定失败");
        }

        if (bindUserId != null) {
            long deviceCount = deviceService.lambdaQuery()
                    .eq(Device::getMerchantId, id)
                    .isNotNull(Device::getUserId)
                    .count();
            if (deviceCount > 0) {
                deviceService.lambdaUpdate()
                        .eq(Device::getMerchantId, id)
                        .isNotNull(Device::getUserId)
                        .set(Device::getUserId, bindUserId)
                        .update();
            }
            return Result.success("绑定成功，已同步 " + deviceCount + " 台设备后续收益账号");
        }

        return Result.success("绑定成功");
    }

    /**
     * 搜索可绑定的平台用户
     */
    @GetMapping("/user-options")
    public Result<?> getUserOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null) {
            return check;
        }

        int limit = Math.min(Math.max(size, 1), 50);
        var query = appUserService.lambdaQuery()
                .orderByDesc(AppUser::getCreateTime);

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            query.and(w -> w.like(AppUser::getNickname, kw)
                    .or().like(AppUser::getPhone, kw)
                    .or().like(AppUser::getId, kw)
                    .or().like(AppUser::getOpenid, kw));
        }

        var users = query.last("LIMIT " + limit).list();
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (AppUser user : users) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("nickname", user.getNickname());
            item.put("phone", user.getPhone());
            item.put("openid", user.getOpenid());
            item.put("merchantId", user.getMerchantId());
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 重置商户密钥
     */
    @PostMapping("/{id}/reset-secret")
    public Result<String> resetSecret(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null)
            return Result.error(check.getMsg());

        ApiMerchant merchant = apiMerchantService.getById(id);
        if (merchant == null) {
            return Result.error("商户不存在");
        }
        merchant.setAppSecret(UUID.randomUUID().toString().replace("-", ""));
        apiMerchantService.updateById(merchant);
        return Result.success("密钥重置成功");
    }

    /**
     * 删除商户
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteMerchant(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null)
            return Result.error(check.getMsg());

        boolean success = apiMerchantService.removeById(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    /**
     * 获取商户名下设备列表（后台查看）
     */
    @GetMapping("/{id}/devices")
    public Result<?> getMerchantDevices(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null)
            return check;

        Page<Device> devicePage = deviceService.lambdaQuery()
                .eq(Device::getMerchantId, id)
                .orderByDesc(Device::getCreateTime)
                .page(new Page<>(page, size));

        java.util.List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (Device device : devicePage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", device.getId());
            item.put("bindCode", device.getBindCode());
            item.put("sn", device.getSn());
            item.put("name", device.getName());
            item.put("userId", device.getUserId());
            item.put("merchantId", device.getMerchantId());
            item.put("status", (device.getStatus() != null && device.getStatus() == 1)
                    || (device.getType() != null && device.getType() == 1) ? 1 : 0);
            item.put("lastHeartbeatTime", device.getLastHeartbeatTime());
            item.put("bindTime", device.getBindTime());
            records.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", devicePage.getTotal());
        return Result.success(data);
    }

    /**
     * 获取商户名下设备收益（按设备+日期）
     */
    @GetMapping("/{id}/device-earnings")
    public Result<?> getMerchantDeviceEarnings(
            @PathVariable Long id,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Result<?> check = checkAdmin(token);
        if (check != null)
            return check;

        LocalDate end = endDate != null && !endDate.isEmpty() ? LocalDate.parse(endDate, DateTimeFormatter.ISO_DATE)
                : LocalDate.now();
        LocalDate start = startDate != null && !startDate.isEmpty()
                ? LocalDate.parse(startDate, DateTimeFormatter.ISO_DATE)
                : end.minusDays(29);

        Page<DeviceEarningsDailyDto> pageParam = new Page<>(page, size);
        com.baomidou.mybatisplus.core.metadata.IPage<DeviceEarningsDailyDto> result = deviceEarningsMapper
                .selectMerchantDeviceDailyEarnings(pageParam, id, start, end);

        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("startDate", start.toString());
        data.put("endDate", end.toString());
        return Result.success(data);
    }

    private void fillBindUserInfo(ApiMerchant merchant) {
        if (merchant.getBindUserId() == null) {
            return;
        }
        AppUser bindUser = appUserService.getById(merchant.getBindUserId());
        if (bindUser == null) {
            return;
        }
        merchant.setBindUserNickname(bindUser.getNickname());
        merchant.setBindUserPhone(bindUser.getPhone());
        merchant.setBalance(bindUser.getBalance() != null ? bindUser.getBalance() : java.math.BigDecimal.ZERO);
    }
}
