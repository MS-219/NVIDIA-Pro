package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.ApiMerchant;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.entity.DeviceOfflineLog;
import com.juxin.orin.entity.SysUser;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IDeviceOfflineLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IDeviceCommandService deviceCommandService;

    @Autowired
    private IDeviceOfflineLogService offlineLogService;

    @Autowired
    private com.juxin.orin.service.IDeviceEarningsService deviceEarningsService;

    @Autowired
    private com.juxin.orin.mapper.DeviceEarningsMapper deviceEarningsMapper;

    @Autowired
    private com.juxin.orin.service.IAppUserService appUserService;

    @Autowired
    private com.juxin.orin.service.IApiMerchantService apiMerchantService;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    @Autowired
    private com.juxin.orin.service.ISysUserService sysUserService;

    /**
     * 验证管理员权限的辅助方法
     * 
     * @return null 表示验证通过，否则返回错误信息
     */
    private String validateAdminToken(String token) {
        return validateAdminToken(token, false);
    }

    private String validateAdminToken(String token, boolean allowFactory) {
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

        Long userId = com.juxin.orin.util.JwtUtil.getUserId(token);
        String tokenUsername = com.juxin.orin.util.JwtUtil.getUsername(token);
        if (userId == null || tokenUsername == null || tokenUsername.isBlank()) {
            return "登录状态异常，请重新登录";
        }
        SysUser currentUser = sysUserService.getById(userId);
        if (currentUser == null) {
            return "账号不存在，请重新登录";
        }
        if (!tokenUsername.equals(currentUser.getUsername())) {
            return "账号信息已变更，请重新登录";
        }
        String currentRole = currentUser.getRole() == null || currentUser.getRole().isBlank()
                ? "admin"
                : currentUser.getRole();
        String tokenRole = com.juxin.orin.util.JwtUtil.getRole(token);
        if (tokenRole == null || tokenRole.isBlank() || !tokenRole.equals(currentRole)) {
            return "账号权限已变更，请重新登录";
        }
        if ("factory".equals(currentRole) && !allowFactory) {
            return "工厂账号仅可导出设备二维码";
        }
        return null;
    }

    /**
     * 验证用户Token并返回用户ID
     * 
     * @return 用户ID，如果验证失败返回null
     */
    private Long validateUserToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return null;
        }
        return com.juxin.orin.util.JwtUtil.getUserId(token);
    }

    // 安卓端心跳上报
    @PostMapping("/heartbeat")
    public Result<Device> heartbeat(@RequestBody Map<String, String> params,
            jakarta.servlet.http.HttpServletRequest request) {
        String sn = params.get("sn");
        String ip = getClientIp(request);

        if (log.isDebugEnabled()) {
            log.debug("[Heartbeat-Debug] SN={}, 解析结果IP={}, X-Real-IP={}, X-Forwarded-For={}, RemoteAddr={}",
                    sn, ip,
                    request.getHeader("X-Real-IP"),
                    request.getHeader("X-Forwarded-For"),
                    request.getRemoteAddr());
        }

        if (sn == null) {
            return Result.error("sn is required");
        }
        Device device;
        try {
            device = deviceService.handleHeartbeat(
                    sn,
                    ip,
                    "0",
                    "0");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }

        // 检查是否有待执行指令，如果有则返回并从数据库清除记录（消费指令）
        if (device != null) {
            DeviceCommand command = deviceCommandService.takePendingCommand(device.getSn());
            if (command != null) {
                Device returnDevice = new Device();
                org.springframework.beans.BeanUtils.copyProperties(device, returnDevice);
                returnDevice.setPendingCommand(command.getCommandText());
                returnDevice.setPendingCommandNo(command.getCommandNo());
                returnDevice.setPendingCommandType(command.getCommandType());

                deviceService.lambdaUpdate()
                        .eq(Device::getId, device.getId())
                        .set(Device::getPendingCommand, null)
                        .update();

                return Result.success(returnDevice);
            }
        }

        if (device != null && device.getPendingCommand() != null && !device.getPendingCommand().isEmpty()) {
            // 克隆一份用于返回，因为我们要把原对象的指令清空
            Device returnDevice = new Device();
            org.springframework.beans.BeanUtils.copyProperties(device, returnDevice);

            // 清除数据库中的指令
            deviceService.lambdaUpdate()
                    .eq(Device::getId, device.getId())
                    .set(Device::getPendingCommand, null)
                    .update();

            return Result.success(returnDevice);
        }

        return Result.success(device);
    }

    /**
     * 推送指令给设备（管理员专用）
     */
    @PostMapping("/push-command")
    public Result<String> pushCommand(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Long id = Long.valueOf(params.get("id").toString());
        String command = (String) params.get("command");

        if (command == null || command.isEmpty()) {
            return Result.error("指令不能为空");
        }

        DeviceCommand queued = deviceCommandService.dispatchCommand(
                id,
                null,
                "CUSTOM",
                command,
                null,
                "legacy push-command");

        return queued != null ? Result.success("指令已推送到队列，等待设备下次心跳执行") : Result.error("推送失败");
    }

    /**
     * 获取客户端真实 IP 地址
     */
    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        // 优先使用 CDN 或代理写入的可靠 Header
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
            // X-Forwarded-For 可能包含多个 IP，如果有多个代理，取最左边第一个（但这可能被客户端伪造，具体取决于 Nginx）
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    // 小程序端绑定 (支持 SN 或绑定码)
    @PostMapping("/bind")
    public Result<String> bind(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String sn = (String) params.get("sn");
        String code = (String) params.get("code"); // 支持绑定码

        Long tokenUserId = validateUserToken(token);
        if (tokenUserId == null) {
            return Result.error("未登录，请先登录");
        }
        String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        if (!"app".equals(com.juxin.orin.util.JwtUtil.getUserType(rawToken))) {
            return Result.error("无权限绑定设备");
        }
        if (sn == null && code == null) {
            return Result.error("SN 或设备码不能为空");
        }

        boolean success = deviceService.bindDevice(sn, code, tokenUserId);
        if (success) {
            return Result.success("绑定成功");
        } else {
            return Result.error("绑定失败：设备不存在或已被绑定");
        }
    }

    // 用户设备列表（需要登录验证）
    @GetMapping("/list")
    public Result<Object> list(
            @RequestParam Long userId,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：用户只能查看自己的设备
        Long tokenUserId = validateUserToken(token);
        if (tokenUserId == null) {
            return Result.error("未登录，请先登录");
        }

        // 检查是否是管理员或查询自己的设备
        String userType = com.juxin.orin.util.JwtUtil.getUserType(
                token.startsWith("Bearer ") ? token.substring(7) : token);
        if (!"admin".equals(userType) && !tokenUserId.equals(userId)) {
            return Result.error("无权查看其他用户的设备");
        }

        java.util.List<Device> devices = deviceService.lambdaQuery().eq(Device::getUserId, userId).list();

        // 转化并补充今日收益数据
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();

        for (Device device : devices) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", device.getId());
            map.put("sn", device.getSn());
            map.put("bindCode", device.getBindCode());
            map.put("name", device.getName());
            map.put("status", device.getStatus());
            map.put("location", device.getLocation() != null ? device.getLocation() : "未知位置");
            map.put("businessId", device.getBusinessId() != null ? device.getBusinessId() : "暂未分配");
            map.put("createTime", device.getCreateTime());
            map.put("bindTime", device.getBindTime());

            // 聚芯算力值统计：计算今日贡献的算力 (运行小时数 * 100)
            int todayHours = deviceEarningsMapper.countByDeviceAndDate(device.getId(), today);
            map.put("hashrate", todayHours * 100);

            // 获取今日收益
            java.math.BigDecimal todayEarnings = deviceEarningsMapper.sumByDeviceAndDate(device.getId(), today);
            map.put("earnings", todayEarnings.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            map.put("todayDate", today.toString());

            result.add(map);
        }

        return Result.success(result);
    }

    // ========== 管理后台 API ==========

    /**
     * 获取全部设备列表（管理后台用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/all")
    public Result<Object> all(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String sn,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Boolean unbound,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：仅管理员可访问
        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Page<Device> pageParam = new Page<>(page, size);

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Device> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        if (sn != null && !sn.isEmpty()) {
            wrapper.and(w -> w.like(Device::getSn, sn).or().like(Device::getBindCode, sn));
        }
        if (status != null) {
            wrapper.eq(Device::getStatus, status);
        }
        if (type != null) {
            if (type == 1) {
                wrapper.eq(Device::getType, 1);
            } else if (type == 0) {
                wrapper.and(w -> w.isNull(Device::getType).or().ne(Device::getType, 1));
            }
        }
        // 未绑定筛选
        if (unbound != null && unbound) {
            wrapper.isNull(Device::getUserId);
        }

        wrapper.orderByDesc(Device::getLastHeartbeatTime);

        Page<Device> result = deviceService.page(pageParam, wrapper);

        // 填充用户信息
        for (Device record : result.getRecords()) {
            if (record.getMerchantId() != null) {
                ApiMerchant merchant = apiMerchantService.getById(record.getMerchantId());
                if (merchant != null) {
                    record.setMerchantName(merchant.getMerchantName());
                }
            }
            if (record.getUserId() != null) {
                com.juxin.orin.entity.AppUser user = appUserService.getById(record.getUserId());
                if (user != null) {
                    record.setNickname(user.getNickname());
                    record.setAvatarUrl(user.getAvatarUrl());
                }
            }
        }

        return Result.success(result);
    }

    /**
     * 获取设备详情（支持用户查看自己的设备或管理员查看）
     */
    @GetMapping("/detail/{id}")
    public Result<Device> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null || token.isEmpty()) {
            return Result.error("未登录");
        }

        // 验证 Token 是否有效
        String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        if (!com.juxin.orin.util.JwtUtil.validateToken(rawToken)) {
            return Result.error("登录已过期");
        }

        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }

        // 权限判定：如果是管理员直接放行，如果是普通用户则检查设备归属
        String userType = com.juxin.orin.util.JwtUtil.getUserType(rawToken);
        Long loggedInUserId = com.juxin.orin.util.JwtUtil.getUserId(rawToken);

        if (!"admin".equals(userType)) {
            if (device.getUserId() == null || !device.getUserId().equals(loggedInUserId)) {
                return Result.error("无权限查看此设备");
            }
        }

        return Result.success(device);
    }

    /**
     * 获取设备离线记录（设备所属用户或管理员可查看）。
     * 记录来自离线检测任务，按离线发生时间倒序返回。
     */
    @GetMapping("/offline-records/{id}")
    public Result<Object> offlineRecords(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null || token.isBlank()) {
            return Result.error("未登录");
        }

        String rawToken = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
        if (!com.juxin.orin.util.JwtUtil.validateToken(rawToken)) {
            return Result.error("登录已过期");
        }

        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }

        String userType = com.juxin.orin.util.JwtUtil.getUserType(rawToken);
        Long loggedInUserId = com.juxin.orin.util.JwtUtil.getUserId(rawToken);
        if (!"admin".equals(userType)
                && (loggedInUserId == null || device.getUserId() == null
                || !device.getUserId().equals(loggedInUserId))) {
            return Result.error("无权限查看此设备的离线记录");
        }

        int safePage = page == null ? 1 : Math.max(1, page);
        int safeSize = size == null ? 20 : Math.max(1, Math.min(50, size));
        Page<DeviceOfflineLog> pageParam = new Page<>(safePage, safeSize);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeviceOfflineLog> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(DeviceOfflineLog::getDeviceId, id)
                .orderByDesc(DeviceOfflineLog::getOfflineTime)
                .orderByDesc(DeviceOfflineLog::getCreateTime);

        return Result.success(offlineLogService.page(pageParam, wrapper));
    }

    /**
     * 更新设备信息
     */
    @PostMapping("/update")
    public Result<String> update(
            @RequestBody Device device,
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null || token.isEmpty()) {
            return Result.error("未登录");
        }

        // 验证 Token 是否有效
        String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        if (!com.juxin.orin.util.JwtUtil.validateToken(rawToken)) {
            return Result.error("登录已过期");
        }

        if (device.getId() == null) {
            return Result.error("设备ID不能为空");
        }

        Device existingDevice = deviceService.getById(device.getId());
        if (existingDevice == null) {
            return Result.error("设备不存在");
        }

        // 权限判定：如果是管理员直接放行，如果是普通用户则检查设备归属
        String userType = com.juxin.orin.util.JwtUtil.getUserType(rawToken);
        Long loggedInUserId = com.juxin.orin.util.JwtUtil.getUserId(rawToken);

        if (!"admin".equals(userType)) {
            if (existingDevice.getUserId() == null || !existingDevice.getUserId().equals(loggedInUserId)) {
                return Result.error("无权限编辑此设备");
            }
            // 普通用户只能编辑名称（作为备注）
            Device updateDevice = new Device();
            updateDevice.setId(device.getId());
            updateDevice.setName(device.getName());
            boolean success = deviceService.updateById(updateDevice);
            return success ? Result.success("更新成功") : Result.error("更新失败");
        }

        boolean success = deviceService.updateById(device);
        return success ? Result.success("更新成功") : Result.error("更新失败");
    }

    /**
     * 解绑设备
     */
    @PostMapping("/unbind")
    public Result<String> unbind(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null || token.isEmpty()) {
            return Result.error("未登录");
        }

        // 验证 Token 是否有效
        String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        if (!com.juxin.orin.util.JwtUtil.validateToken(rawToken)) {
            return Result.error("登录已过期");
        }

        Long id = Long.valueOf(params.get("id").toString());
        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }

        // 权限判定：如果是管理员直接放行，如果是普通用户则检查设备归属
        String userType = com.juxin.orin.util.JwtUtil.getUserType(rawToken);
        Long loggedInUserId = com.juxin.orin.util.JwtUtil.getUserId(rawToken);

        if (!"admin".equals(userType)) {
            if (device.getUserId() == null || !device.getUserId().equals(loggedInUserId)) {
                return Result.error("无权限解绑此设备");
            }
        }

        // 使用 UpdateWrapper 强制更新 null 值
        boolean success = deviceService.lambdaUpdate()
                .eq(Device::getId, id)
                .set(Device::getUserId, null)
                .set(Device::getBusinessId, null)
                .set(Device::getBindTime, null)
                .update();

        return success ? Result.success("解绑成功") : Result.error("解绑失败");
    }

    /**
     * 批量解绑设备（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/batch-unbind")
    public Result<String> batchUnbind(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Object idsObj = params.get("ids");
        if (idsObj == null) {
            return Result.error("设备ID列表不能为空");
        }

        java.util.List<?> list = (java.util.List<?>) idsObj;
        if (list.isEmpty()) {
            return Result.error("请选择要解绑的设备");
        }

        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (Object item : list) {
            ids.add(Long.valueOf(item.toString()));
        }

        boolean success = deviceService.lambdaUpdate()
                .in(Device::getId, ids)
                .set(Device::getUserId, null)
                .set(Device::getBusinessId, null)
                .set(Device::getBindTime, null)
                .update();

        return success ? Result.success("批量解绑成功") : Result.error("批量解绑失败");
    }

    /**
     * 退回商户设备（管理员专用）
     * 清除用户绑定和商户归属，使设备重新进入公共可绑定池。
     */
    @PostMapping("/release-merchant")
    public Result<String> releaseMerchantDevice(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Object idObj = params.get("id");
        if (idObj == null) {
            return Result.error("设备ID不能为空");
        }

        Long id;
        try {
            id = Long.valueOf(idObj.toString());
        } catch (NumberFormatException e) {
            return Result.error("设备ID格式错误");
        }

        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }
        if (device.getMerchantId() == null) {
            return Result.error("设备不属于商户，无需退回");
        }

        boolean success = deviceService.lambdaUpdate()
                .eq(Device::getId, id)
                .set(Device::getUserId, null)
                .set(Device::getMerchantId, null)
                .set(Device::getBusinessId, null)
                .set(Device::getBindTime, null)
                .set(Device::getLastPayTime, null)
                .update();

        if (success) {
            log.info("管理员退回商户设备成功: deviceId={}, sn={}, merchantId={}",
                    device.getId(), device.getSn(), device.getMerchantId());
            try {
                appUserService.updateAllUserLevels();
            } catch (Exception e) {
                log.warn("退回商户设备后刷新用户等级失败: deviceId={}, error={}", id, e.getMessage());
            }
        }

        return success ? Result.success("设备已退回，可重新绑定") : Result.error("退回失败");
    }

    /**
     * 批量退回商户设备（管理员专用）
     */
    @PostMapping("/batch-release-merchant")
    public Result<String> batchReleaseMerchantDevice(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Object idsObj = params.get("ids");
        if (!(idsObj instanceof java.util.List<?> list) || list.isEmpty()) {
            return Result.error("设备ID列表不能为空");
        }

        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (Object item : list) {
            ids.add(Long.valueOf(item.toString()));
        }

        long targetCount = deviceService.lambdaQuery()
                .in(Device::getId, ids)
                .isNotNull(Device::getMerchantId)
                .count();
        if (targetCount <= 0) {
            return Result.error("未找到可退回的商户设备");
        }

        boolean success = deviceService.lambdaUpdate()
                .in(Device::getId, ids)
                .isNotNull(Device::getMerchantId)
                .set(Device::getUserId, null)
                .set(Device::getMerchantId, null)
                .set(Device::getBusinessId, null)
                .set(Device::getBindTime, null)
                .set(Device::getLastPayTime, null)
                .update();

        if (success) {
            log.info("管理员批量退回商户设备成功: deviceIds={}, count={}", ids, targetCount);
            try {
                appUserService.updateAllUserLevels();
            } catch (Exception e) {
                log.warn("批量退回商户设备后刷新用户等级失败: ids={}, error={}", ids, e.getMessage());
            }
        }

        return success ? Result.success("已退回 " + targetCount + " 台商户设备") : Result.error("批量退回失败");
    }

    /**
     * 批量删除未绑定设备（管理员专用）
     */
    @PostMapping("/batch-delete")
    public Result<String> batchDeleteDevice(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Object idsObj = params.get("ids");
        if (!(idsObj instanceof java.util.List<?> list) || list.isEmpty()) {
            return Result.error("设备ID列表不能为空");
        }

        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (Object item : list) {
            ids.add(Long.valueOf(item.toString()));
        }

        java.util.List<Long> deletableIds = deviceService.lambdaQuery()
                .in(Device::getId, ids)
                .isNull(Device::getUserId)
                .isNull(Device::getMerchantId)
                .list()
                .stream()
                .map(Device::getId)
                .toList();
        if (deletableIds.isEmpty()) {
            return Result.error("未找到可删除的未绑定设备");
        }

        boolean success = deviceService.removeByIds(deletableIds);
        if (success) {
            log.info("管理员批量删除设备成功: deviceIds={}", deletableIds);
        }

        return success ? Result.success("已删除 " + deletableIds.size() + " 台设备") : Result.error("批量删除失败");
    }

    /**
     * 管理员绑定设备到用户（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin-bind")
    public Result<String> adminBind(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        if (params == null || params.get("deviceId") == null || params.get("userId") == null) {
            return Result.error("设备ID和用户ID不能为空");
        }

        Long deviceId;
        Long userId;
        try {
            deviceId = Long.valueOf(params.get("deviceId").toString());
            userId = Long.valueOf(params.get("userId").toString());
        } catch (NumberFormatException e) {
            return Result.error("设备ID或用户ID格式不正确");
        }
        if (deviceId <= 0 || userId <= 0) {
            return Result.error("设备ID或用户ID格式不正确");
        }

        Device device = deviceService.getById(deviceId);
        if (device == null) {
            return Result.error("设备不存在");
        }

        if (device.getUserId() != null) {
            return Result.error("设备已被绑定，请先解绑");
        }

        // 检查用户是否存在
        com.juxin.orin.entity.AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 执行绑定
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        boolean success = deviceService.lambdaUpdate()
                .eq(Device::getId, deviceId)
                .isNull(Device::getUserId)
                .set(Device::getUserId, userId)
                .set(Device::getBindTime, now)
                .set(Device::getLastPayTime, now) // 初始化结算时间，让设备可以立即产生收益
                .update();

        return success ? Result.success("绑定成功") : Result.error("绑定失败");
    }

    /**
     * 获取设备统计（管理员专用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/stats")
    public Result<Object> stats(
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        long total = deviceService.count();
        long online = deviceService.lambdaQuery().eq(Device::getStatus, 1).count();
        long offline = deviceService.lambdaQuery().eq(Device::getStatus, 0).count();
        long bound = deviceService.lambdaQuery().isNotNull(Device::getUserId).count();
        long attached = deviceService.lambdaQuery().eq(Device::getType, 1).count();
        long real = deviceService.lambdaQuery()
                .and(w -> w.isNull(Device::getType).or().ne(Device::getType, 1))
                .count();

        return Result.success(Map.of(
                "total", total,
                "online", online,
                "offline", offline,
                "bound", bound,
                "real", real,
                "virtual", attached));
    }

    // ========== SN 管理 API ==========

    /**
     * 导出所有设备 SN 列表（给厂家打印贴纸）（管理员专用）
     * 安全修复：需要管理员权限
     * 返回 CSV 格式：序号,SN码,状态,创建时间
     */
    @GetMapping("/export-sn")
    public Result<Object> exportSn(
            @RequestParam(required = false) Boolean unboundOnly,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token, true);
        if (error != null) {
            return Result.error(error);
        }

        com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<Device> query = deviceService
                .lambdaQuery();
        String rawToken = normalizeToken(token);
        String role = com.juxin.orin.util.JwtUtil.getRole(rawToken);
        if (Boolean.TRUE.equals(unboundOnly) || "factory".equals(role)) {
            query.isNull(Device::getUserId);
        }
        if ("factory".equals(role)) {
            return Result.success(Map.of(
                    "total", 0,
                    "list", java.util.List.of()));
        }

        query.orderByDesc(Device::getCreateTime);

        java.util.List<Device> devices = query.list();
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();

        int index = 1;
        for (Device device : devices) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("index", index++);
            item.put("sn", device.getSn());
            item.put("bindCode", device.getBindCode() != null ? device.getBindCode() : "");
            item.put("status", device.getStatus() != null && device.getStatus() == 1 ? "在线" : "离线");
            item.put("bound", device.getUserId() != null ? "已绑定" : "未绑定");
            item.put("createTime", device.getCreateTime() != null ? device.getCreateTime().toString() : "");
            result.add(item);
        }

        return Result.success(Map.of(
                "total", devices.size(),
                "list", result));
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String rawToken = token.trim();
        return rawToken.startsWith("Bearer ") ? rawToken.substring(7).trim() : rawToken;
    }

    /**
     * 通过 SN 查询设备信息（小程序扫码绑定用）
     */
    @GetMapping("/query-by-sn")
    public Result<Object> queryBySn(@RequestParam String sn) {
        Device device = deviceService.lambdaQuery().eq(Device::getSn, sn).one();

        if (device == null) {
            return Result.error("设备不存在，请检查 SN 码是否正确");
        }

        // 返回设备基本信息（不含敏感数据）
        return Result.success(Map.of(
                "id", device.getId(),
                "sn", device.getSn(),
                "status", device.getStatus(),
                "bound", device.getUserId() != null,
                "name", device.getName() != null ? device.getName() : "聚芯算力节点"));
    }

    /**
     * 通过绑定码查询设备信息（小程序扫码绑定用）
     */
    /**
     * 通过绑定码查询设备信息（小程序扫码绑定用）
     */
    @GetMapping("/query-by-code")
    public Result<Object> queryByCode(@RequestParam String code) {
        if (code == null || code.trim().isEmpty()) {
            return Result.error("设备码不能为空");
        }
        code = code.trim(); // 去除空格

        // 1. 优先通过绑定码查询 (精确匹配)
        Device device = deviceService.lambdaQuery().eq(Device::getBindCode, code).one();

        // 2. 如果没查到，尝试大写匹配 (防止大小写差异)
        if (device == null) {
            device = deviceService.lambdaQuery().eq(Device::getBindCode, code.toUpperCase()).one();
        }
        // 3. 尝试小写匹配
        if (device == null) {
            device = deviceService.lambdaQuery().eq(Device::getBindCode, code.toLowerCase()).one();
        }

        // 4. 只有在前三步都失败时，才尝试 SN (SN通常很长，不容易混淆)
        if (device == null) {
            device = deviceService.lambdaQuery().eq(Device::getSn, code).one();
        }

        if (device == null) {
            return Result.error("设备不存在，请检查设备码是否正确");
        }

        // 返回设备基本信息（不含敏感数据）
        return Result.success(Map.of(
                "id", device.getId(),
                "sn", device.getSn(),
                "bindCode", device.getBindCode() != null ? device.getBindCode() : "",
                "status", device.getStatus(),
                "bound", device.getUserId() != null,
                "name", device.getName() != null ? device.getName() : "聚芯算力节点"));
    }

    /**
     * 获取未绑定设备列表（管理员专用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/unbound")
    public Result<Object> unboundDevices(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Page<Device> pageParam = new Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Device> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.isNull(Device::getUserId);
        wrapper.orderByDesc(Device::getCreateTime);

        return Result.success(deviceService.page(pageParam, wrapper));
    }

    /**
     * 获取设备最近7天收益图表数据
     */
    @GetMapping("/chart-data/{id}")
    public Result<Object> chartData(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null || token.isEmpty()) {
            return Result.error("未登录");
        }

        // 验证 Token 是否有效
        String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        if (!com.juxin.orin.util.JwtUtil.validateToken(rawToken)) {
            return Result.error("登录已过期");
        }

        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }

        // 权限判定：如果是管理员直接放行，如果是普通用户则检查设备归属
        String userType = com.juxin.orin.util.JwtUtil.getUserType(rawToken);
        Long loggedInUserId = com.juxin.orin.util.JwtUtil.getUserId(rawToken);

        if (!"admin".equals(userType)) {
            if (device.getUserId() == null || !device.getUserId().equals(loggedInUserId)) {
                return Result.error("无权限查看此设备数据");
            }
        }
        // 获取最近7天的日期
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.List<String> dates = new java.util.ArrayList<>();
        java.util.List<java.math.BigDecimal> earnings = new java.util.ArrayList<>();

        // 准备日期列表（倒序）
        for (int i = 6; i >= 0; i--) {
            dates.add(today.minusDays(i).toString());
            earnings.add(java.math.BigDecimal.ZERO); // 默认0
        }

        // 查询收益记录 (最近7天)
        java.time.LocalDate startDate = today.minusDays(6);
        java.util.List<com.juxin.orin.entity.DeviceEarnings> list = deviceEarningsService.lambdaQuery()
                .eq(com.juxin.orin.entity.DeviceEarnings::getDeviceId, id)
                .ge(com.juxin.orin.entity.DeviceEarnings::getDate, startDate)
                .le(com.juxin.orin.entity.DeviceEarnings::getDate, today)
                .list();

        // 填充数据 - 需要按日期分组求和
        // 初始化映射表方便后续查找累加
        java.util.Map<String, java.math.BigDecimal> dateMap = new java.util.HashMap<>();
        for (String date : dates) {
            dateMap.put(date, java.math.BigDecimal.ZERO);
        }

        // 遍历记录并累加到对应日期
        for (com.juxin.orin.entity.DeviceEarnings item : list) {
            String dateStr = item.getDate().toString();
            if (dateMap.containsKey(dateStr)) {
                java.math.BigDecimal current = dateMap.get(dateStr);
                dateMap.put(dateStr, current.add(item.getAmount()));
            }
        }

        // 将 Map 转回 List
        for (int i = 0; i < dates.size(); i++) {
            earnings.set(i, dateMap.get(dates.get(i)));
        }

        return Result.success(java.util.Map.of(
                "dates", dates,
                "earnings", earnings));
    }

    // ========== 挂靠设备管理 API ==========

    /**
     * 新增挂靠设备（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/create")
    public Result<Object> createDevice(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        String name = (String) params.get("name");
        Integer hashrate = params.get("hashrate") != null ? Integer.valueOf(params.get("hashrate").toString()) : 100;
        String carrier = (String) params.get("carrier");
        String location = (String) params.get("location");

        Device device = new Device();
        // 生成挂靠设备 SN (VD + 时间戳 + 随机数)
        device.setSn("VD" + System.currentTimeMillis() + (int) (Math.random() * 1000));
        // 生成绑定码
        device.setBindCode(generateBindCode());
        device.setName(name);
        device.setHashrate(hashrate);
        device.setCarrier(carrier); // 设置运营商
        device.setLocation(location); // 设置位置
        device.setType(1); // 挂靠设备
        device.setStatus(1); // 始终在线
        device.setCreateTime(java.time.LocalDateTime.now());
        device.setLastHeartbeatTime(java.time.LocalDateTime.now());

        // 自动分配业务号（如果配置开启）
        String autoAssign = configService.getConfig("device.autoAssignBusiness", "true");
        if (Boolean.parseBoolean(autoAssign)) {
            device.setBusinessId("YW" + System.currentTimeMillis());
        }

        boolean success = deviceService.save(device);
        if (success) {
            return Result.success(device);
        } else {
            return Result.error("创建失败");
        }
    }

    /**
     * 批量新增挂靠设备（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/batch-create")
    public Result<Object> batchCreateDevice(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Integer count = params.get("count") != null ? Integer.valueOf(params.get("count").toString()) : 1;
        Integer hashrate = params.get("hashrate") != null ? Integer.valueOf(params.get("hashrate").toString()) : 100;
        Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
        String carrier = (String) params.get("carrier"); // 获取批量设置的运营商
        String location = (String) params.get("location"); // 获取批量设置的位置

        if (count <= 0 || count > 100) {
            return Result.error("数量必须在 1-100 之间");
        }

        // 如果指定了用户ID，验证用户存在
        if (userId != null) {
            com.juxin.orin.entity.AppUser user = appUserService.getById(userId);
            if (user == null) {
                return Result.error("指定的用户不存在");
            }
        }

        java.util.List<Device> devices = new java.util.ArrayList<>();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        for (int i = 0; i < count; i++) {
            Device device = new Device();
            device.setSn("VD" + System.currentTimeMillis() + (int) (Math.random() * 1000));
            device.setBindCode(generateBindCode());
            device.setHashrate(hashrate);
            device.setCarrier(carrier); // 设置运营商
            device.setLocation(location); // 设置位置
            device.setType(1); // 挂靠设备
            device.setStatus(1); // 始终在线
            device.setCreateTime(now);
            device.setLastHeartbeatTime(now);

            // 如果指定了用户，直接绑定
            if (userId != null) {
                device.setUserId(userId);
                device.setBindTime(now);
                device.setLastPayTime(now); // 初始化结算时间，使设备可以参与收益计算

                // 自动分配业务号（如果配置开启）
                String autoAssign = configService.getConfig("device.autoAssignBusiness", "true");
                if (Boolean.parseBoolean(autoAssign)) {
                    device.setBusinessId("YW" + System.currentTimeMillis());
                }
            }

            devices.add(device);

            // 添加短暂延迟确保 SN 唯一
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }

        boolean success = deviceService.saveBatch(devices);
        return Result.success(Map.of("created", success ? devices.size() : 0));
    }

    /**
     * 删除设备（仅限未绑定设备）（管理员专用）
     * 安全修复：需要管理员权限
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> deleteDevice(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }
        if (device.getUserId() != null) {
            return Result.error("设备已绑定用户，无法删除");
        }
        if (device.getMerchantId() != null) {
            return Result.error("设备仍归属商户，请先退回商户设备");
        }

        boolean success = deviceService.removeById(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    /**
     * 生成8位绑定码 (JX + 6位字母数字混合)
     */
    private String generateBindCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.util.Random random = new java.util.Random();
        String code;
        int attempts = 0;
        do {
            StringBuilder sb = new StringBuilder("JX");
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            code = sb.toString();
            attempts++;
            if (attempts > 100) {
                code = "JX" + System.currentTimeMillis() % 1000000;
                break;
            }
        } while (deviceService.lambdaQuery().eq(Device::getBindCode, code).exists());
        return code;
    }
}
