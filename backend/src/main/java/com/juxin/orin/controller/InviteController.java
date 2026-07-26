package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IInviteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 邀请好友控制器
 */
@RestController
@RequestMapping("/api/invite")
public class InviteController {

    @Autowired
    private IInviteService inviteService;

    @Autowired
    private IAppUserService appUserService;

    @Autowired
    private com.juxin.orin.service.IDeviceService deviceService;

    /**
     * 获取用户的邀请统计信息
     * 
     * @param userId 用户ID
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getInviteStats(@RequestParam Long userId) {
        Map<String, Object> stats = inviteService.getInviteStats(userId);
        return Result.success(stats);
    }

    /**
     * 验证邀请码是否有效
     * 
     * @param code 邀请码
     */
    @GetMapping("/validate")
    public Result<Map<String, Object>> validateInviteCode(@RequestParam String code) {
        Long inviterId = inviteService.getInviterIdByCode(code);
        if (inviterId != null) {
            AppUser inviter = appUserService.getById(inviterId);
            return Result.success(Map.of(
                    "valid", true,
                    "inviterId", inviterId,
                    "inviterNickname", inviter != null && inviter.getNickname() != null ? inviter.getNickname() : ""));
        } else {
            return Result.success(Map.of(
                    "valid", false));
        }
    }

    /**
     * 用户手动绑定邀请码
     * 绑定后用户不可更改，只能后台修改
     */
    @PostMapping("/bind")
    public Result<String> bindInviter(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String inviteCode = (String) params.get("inviteCode");

        if (inviteCode == null || inviteCode.isEmpty()) {
            return Result.error("邀请码不能为空");
        }

        // 检查用户是否已绑定邀请人
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (user.getInviterId() != null) {
            return Result.error("您已绑定邀请人，不可更改");
        }

        // 验证邀请码
        Long inviterId = inviteService.getInviterIdByCode(inviteCode);
        if (inviterId == null) {
            return Result.error("邀请码无效");
        }

        // 不能自己邀请自己
        if (inviterId.equals(userId)) {
            return Result.error("不能填写自己的邀请码");
        }

        // 绑定邀请关系
        inviteService.handleNewUserInvite(userId, inviteCode);

        return Result.success("绑定成功");
    }

    /**
     * 获取用户的邀请人信息
     */
    @GetMapping("/inviter")
    public Result<Map<String, Object>> getInviter(@RequestParam Long userId) {
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (user.getInviterId() == null) {
            return Result.success(Map.of("hasInviter", false));
        }

        AppUser inviter = appUserService.getById(user.getInviterId());
        if (inviter == null) {
            return Result.success(Map.of("hasInviter", false));
        }

        return Result.success(Map.of(
                "hasInviter", true,
                "inviterId", inviter.getId(),
                "inviterNickname", inviter.getNickname() != null ? inviter.getNickname() : "用户" + inviter.getId(),
                "inviterAvatar", inviter.getAvatarUrl() != null ? inviter.getAvatarUrl() : ""));
    }

    /**
     * 获取伙伴设备列表（小程序用）
     * 返回当前用户邀请的所有用户及其设备统计（总数、在线、离线）
     */
    @GetMapping("/partner-devices")
    public Result<Object> getPartnerDevices(@RequestParam Long userId) {
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 查询被该用户直接邀请的下级用户
        java.util.List<AppUser> invitedUsers = appUserService.lambdaQuery()
                .eq(AppUser::getInviterId, userId)
                .orderByDesc(AppUser::getCreateTime)
                .list();

        // 构建结果列表
        java.util.List<java.util.Map<String, Object>> members = new java.util.ArrayList<>();

        int totalDevices = 0;
        int totalOnline = 0;
        int totalOffline = 0;

        for (AppUser member : invitedUsers) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", member.getId());
            item.put("nickname", member.getNickname() != null ? member.getNickname() : "用户" + member.getId());
            item.put("avatarUrl", member.getAvatarUrl() != null ? member.getAvatarUrl() : "");
            item.put("createTime", member.getCreateTime());
            item.put("level", member.getLevel() != null ? member.getLevel() : 0);

            // 该用户自己的设备总数
            long memberDevices = deviceService.lambdaQuery()
                    .eq(com.juxin.orin.entity.Device::getUserId, member.getId())
                    .count();

            // 该用户自己的在线设备数 (status=1 或 type=1 表示虚拟设备永不离线)
            long memberOnline = deviceService.lambdaQuery()
                    .eq(com.juxin.orin.entity.Device::getUserId, member.getId())
                    .and(w -> w.eq(com.juxin.orin.entity.Device::getStatus, 1)
                            .or().eq(com.juxin.orin.entity.Device::getType, 1))
                    .count();

            long memberOffline = memberDevices - memberOnline;

            item.put("deviceCount", memberDevices);
            item.put("onlineCount", memberOnline);
            item.put("offlineCount", memberOffline);

            members.add(item);

            totalDevices += memberDevices;
            totalOnline += memberOnline;
            totalOffline += memberOffline;
        }

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("members", members);
        data.put("totalMembers", invitedUsers.size());
        data.put("totalDevices", totalDevices);
        data.put("totalOnline", totalOnline);
        data.put("totalOffline", totalOffline);

        return Result.success(data);
    }

    /**
     * 获取指定用户的设备列表详情（小程序用）
     * 用于查看伙伴的设备状态、收益等信息
     */
    @GetMapping("/member-devices")
    public Result<Object> getMemberDevices(
            @RequestParam Long userId,
            @RequestParam Long memberId,
            @RequestParam(required = false) String status) {

        // 验证请求者是否有权限查看（必须是邀请人）
        AppUser member = appUserService.getById(memberId);
        if (member == null) {
            return Result.error("用户不存在");
        }
        if (member.getInviterId() == null || !member.getInviterId().equals(userId)) {
            return Result.error("无权查看该用户的设备");
        }

        // 查询该用户的设备列表
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.juxin.orin.entity.Device> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(com.juxin.orin.entity.Device::getUserId, memberId);

        // 根据状态筛选
        if ("online".equals(status)) {
            wrapper.and(w -> w.eq(com.juxin.orin.entity.Device::getStatus, 1)
                    .or().eq(com.juxin.orin.entity.Device::getType, 1));
        } else if ("offline".equals(status)) {
            // 离线设备：status 为 0 或 null，且不是虚拟设备
            wrapper.and(w -> w.isNull(com.juxin.orin.entity.Device::getStatus)
                    .or().eq(com.juxin.orin.entity.Device::getStatus, 0))
                    .and(w -> w.isNull(com.juxin.orin.entity.Device::getType)
                            .or().ne(com.juxin.orin.entity.Device::getType, 1));
        }

        wrapper.orderByDesc(com.juxin.orin.entity.Device::getCreateTime);
        java.util.List<com.juxin.orin.entity.Device> devices = deviceService.list(wrapper);

        // 构建结果
        java.util.List<java.util.Map<String, Object>> deviceList = new java.util.ArrayList<>();
        int onlineCount = 0;
        int offlineCount = 0;

        for (com.juxin.orin.entity.Device device : devices) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", device.getId());
            item.put("bindCode", device.getBindCode());
            item.put("name", device.getName() != null ? device.getName() : "设备" + device.getBindCode());
            item.put("type", device.getType()); // 0=实体设备, 1=虚拟设备

            // 判断在线状态：status=1 或 虚拟设备(type=1)永不离线
            boolean isOnline = (device.getStatus() != null && device.getStatus() == 1)
                    || (device.getType() != null && device.getType() == 1);
            item.put("status", isOnline ? 1 : 0);
            item.put("statusText", isOnline ? "在线" : "离线");

            if (isOnline)
                onlineCount++;
            else
                offlineCount++;

            item.put("lastHeartbeat", device.getLastHeartbeatTime());
            item.put("createTime", device.getCreateTime());

            deviceList.add(item);
        }

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("devices", deviceList);
        data.put("totalCount", devices.size());
        data.put("onlineCount", onlineCount);
        data.put("offlineCount", offlineCount);

        // 用户信息
        data.put("memberNickname", member.getNickname() != null ? member.getNickname() : "用户" + memberId);
        data.put("memberAvatar", member.getAvatarUrl() != null ? member.getAvatarUrl() : "");
        data.put("memberLevel", member.getLevel() != null ? member.getLevel() : 0);

        return Result.success(data);
    }

    /**
     * 获取所有伙伴的设备列表（小程序用）
     * 可按状态筛选：online/offline/all
     */
    @GetMapping("/all-partner-devices")
    public Result<Object> getAllPartnerDevicesList(
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "all") String status) {

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 查询所有直接邀请的下级用户
        java.util.List<AppUser> invitedUsers = appUserService.lambdaQuery()
                .eq(AppUser::getInviterId, userId)
                .list();

        if (invitedUsers.isEmpty()) {
            java.util.Map<String, Object> emptyData = new java.util.HashMap<>();
            emptyData.put("devices", new java.util.ArrayList<>());
            emptyData.put("totalCount", 0);
            emptyData.put("onlineCount", 0);
            emptyData.put("offlineCount", 0);
            return Result.success(emptyData);
        }

        // 获取所有下级用户的ID
        java.util.List<Long> memberIds = invitedUsers.stream()
                .map(AppUser::getId)
                .collect(java.util.stream.Collectors.toList());

        // 构建用户ID到用户信息的映射
        java.util.Map<Long, AppUser> userMap = invitedUsers.stream()
                .collect(java.util.stream.Collectors.toMap(AppUser::getId, u -> u));

        // 查询所有下级用户的设备
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.juxin.orin.entity.Device> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.in(com.juxin.orin.entity.Device::getUserId, memberIds);

        // 根据状态筛选
        if ("online".equals(status)) {
            wrapper.and(w -> w.eq(com.juxin.orin.entity.Device::getStatus, 1)
                    .or().eq(com.juxin.orin.entity.Device::getType, 1));
        } else if ("offline".equals(status)) {
            // 离线设备：status 为 0 或 null，且不是虚拟设备
            wrapper.and(w -> w.isNull(com.juxin.orin.entity.Device::getStatus)
                    .or().eq(com.juxin.orin.entity.Device::getStatus, 0))
                    .and(w -> w.isNull(com.juxin.orin.entity.Device::getType)
                            .or().ne(com.juxin.orin.entity.Device::getType, 1));
        }

        wrapper.orderByDesc(com.juxin.orin.entity.Device::getLastHeartbeatTime);
        java.util.List<com.juxin.orin.entity.Device> devices = deviceService.list(wrapper);

        // 构建结果
        java.util.List<java.util.Map<String, Object>> deviceList = new java.util.ArrayList<>();
        int onlineCount = 0;
        int offlineCount = 0;

        for (com.juxin.orin.entity.Device device : devices) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", device.getId());
            item.put("bindCode", device.getBindCode());
            item.put("name", device.getName() != null ? device.getName() : "设备" + device.getBindCode());
            item.put("type", device.getType());

            // 判断在线状态
            boolean isOnline = (device.getStatus() != null && device.getStatus() == 1)
                    || (device.getType() != null && device.getType() == 1);
            item.put("status", isOnline ? 1 : 0);
            item.put("statusText", isOnline ? "在线" : "离线");

            if (isOnline)
                onlineCount++;
            else
                offlineCount++;

            item.put("lastHeartbeat", device.getLastHeartbeatTime());
            item.put("createTime", device.getCreateTime());

            // 添加设备所属用户信息
            AppUser owner = userMap.get(device.getUserId());
            if (owner != null) {
                item.put("ownerId", owner.getId());
                item.put("ownerNickname", owner.getNickname() != null ? owner.getNickname() : "用户" + owner.getId());
                item.put("ownerAvatar", owner.getAvatarUrl() != null ? owner.getAvatarUrl() : "");
                item.put("ownerLevel", owner.getLevel() != null ? owner.getLevel() : 0);
            }

            deviceList.add(item);
        }

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("devices", deviceList);
        data.put("totalCount", deviceList.size());
        data.put("onlineCount", onlineCount);
        data.put("offlineCount", offlineCount);
        data.put("filterStatus", status);

        return Result.success(data);
    }


    /**
     * 获取伙伴设备详情及收益图表（小程序用）
     * 允许邀请人查看被邀请人的设备收益信息
     */
    @GetMapping("/partner-device-detail")
    public Result<Object> getPartnerDeviceDetail(
            @RequestParam Long userId,
            @RequestParam Long deviceId) {

        // 获取设备信息
        com.juxin.orin.entity.Device device = deviceService.getById(deviceId);
        if (device == null) {
            return Result.error("设备不存在");
        }

        // 验证权限：设备所属用户必须是当前用户的直接邀请人
        Long deviceOwnerId = device.getUserId();
        if (deviceOwnerId == null) {
            return Result.error("该设备未绑定用户");
        }

        AppUser deviceOwner = appUserService.getById(deviceOwnerId);
        if (deviceOwner == null || deviceOwner.getInviterId() == null ||
                !deviceOwner.getInviterId().equals(userId)) {
            return Result.error("无权查看该设备信息");
        }

        // 构建设备详情
        java.util.Map<String, Object> deviceInfo = new java.util.HashMap<>();
        deviceInfo.put("id", device.getId());
        deviceInfo.put("bindCode", device.getBindCode());
        deviceInfo.put("name", device.getName() != null ? device.getName() : "设备" + device.getBindCode());
        deviceInfo.put("type", device.getType());

        boolean isOnline = (device.getStatus() != null && device.getStatus() == 1)
                || (device.getType() != null && device.getType() == 1);
        deviceInfo.put("status", isOnline ? 1 : 0);
        deviceInfo.put("statusText", isOnline ? "在线" : "离线");
        deviceInfo.put("lastHeartbeat", device.getLastHeartbeatTime());
        deviceInfo.put("bindTime", device.getBindTime());
        deviceInfo.put("location", device.getLocation());

        // 用户信息
        deviceInfo.put("ownerId", deviceOwner.getId());
        deviceInfo.put("ownerNickname",
                deviceOwner.getNickname() != null ? deviceOwner.getNickname() : "用户" + deviceOwner.getId());
        deviceInfo.put("ownerAvatar", deviceOwner.getAvatarUrl());

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("device", deviceInfo);

        return Result.success(data);
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
     * 获取用户的直接下级列表（管理后台用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/admin/team")
    public Result<Object> getTeamList(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        // 分页获取直接邀请的下级用户
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AppUser> pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                page, size);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AppUser> result = appUserService.lambdaQuery()
                .eq(AppUser::getInviterId, userId)
                .orderByDesc(AppUser::getCreateTime)
                .page(pageParam);

        // 填充每个下级的设备数和收益统计
        java.util.List<java.util.Map<String, Object>> records = new java.util.ArrayList<>();
        for (AppUser member : result.getRecords()) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", member.getId());
            item.put("nickname", member.getNickname());
            item.put("avatarUrl", member.getAvatarUrl());
            item.put("phone", member.getPhone());
            item.put("level", member.getLevel());
            item.put("balance", member.getBalance());
            item.put("createTime", member.getCreateTime());

            // 获取下级的设备数 (传入null表示按该下级自身的等级进行统计)
            long deviceCount = inviteService.getTeamDeviceCount(member.getId(), null);
            item.put("deviceCount", deviceCount);

            // 获取该下级邀请的人数
            long subTeamCount = appUserService.lambdaQuery()
                    .eq(AppUser::getInviterId, member.getId())
                    .count();
            item.put("subTeamCount", subTeamCount);

            records.add(item);
        }

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("current", result.getCurrent());

        return Result.success(data);
    }

    /**
     * 获取所有有下级的用户列表（用于筛选）（管理员专用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/admin/leaders")
    public Result<Object> getLeaders(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "team") String sortBy,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        // 查询所有用户
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AppUser> pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                page, size);

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AppUser> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("nickname", keyword)
                    .or().like("id", keyword));
        }

        // 核心改动：使用子查询统计直接下级人数并进行排序
        wrapper.select("*, (SELECT COUNT(*) FROM app_user child "
                + "WHERE child.inviter_id = app_user.id AND child.deleted = 0) as team_count_internal");

        if ("team".equals(sortBy)) {
            wrapper.orderByDesc("team_count_internal");
        } else {
            wrapper.orderByDesc("create_time");
        }

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AppUser> result = appUserService.page(pageParam,
                wrapper);

        // 填充每个用户的统计数据
        java.util.List<java.util.Map<String, Object>> records = new java.util.ArrayList<>();
        for (AppUser user : result.getRecords()) {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", user.getId());
            item.put("nickname", user.getNickname());
            item.put("avatarUrl", user.getAvatarUrl());
            item.put("level", user.getLevel());
            item.put("createTime", user.getCreateTime());

            // 获取直接下级数
            long teamCount = appUserService.lambdaQuery()
                    .eq(AppUser::getInviterId, user.getId())
                    .count();
            item.put("teamCount", teamCount);

            // 获取团队设备数 (传入null表示按该用户自身的等级进行统计)
            long deviceCount = inviteService.getTeamDeviceCount(user.getId(), null);
            item.put("teamDeviceCount", deviceCount);

            records.add(item);
        }

        // 如果是按设备数排序，且当前页数据较少，在内存中进一步精细排序
        if ("team".equals(sortBy)) {
            records.sort((a, b) -> Long.compare((long) b.get("teamDeviceCount"), (long) a.get("teamDeviceCount")));
        }

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("current", result.getCurrent());

        return Result.success(data);
    }
}
