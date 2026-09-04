package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.InviteReward;
import com.juxin.orin.mapper.InviteRewardMapper;
import com.juxin.orin.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 邀请奖励管理（管理后台使用）
 */
@RestController
@RequestMapping("/api/admin/invite-reward")
public class AdminInviteRewardController {

    @Autowired
    private InviteRewardMapper inviteRewardMapper;

    @Autowired
    private IAppUserService appUserService;

    /**
     * 获取全系统邀请奖励列表（管理员专用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/list")
    public Result<Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long inviterId,
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

        Page<InviteReward> pageParam = new Page<>(page, size);
        Page<InviteReward> result = inviteRewardMapper.selectPage(pageParam,
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InviteReward>()
                        .eq(inviterId != null, "inviter_id", inviterId)
                        .orderByDesc("create_time"));

        // 填充邀请人和被邀请人的基本信息，方便后台显示
        java.util.List<Map<String, Object>> records = result.getRecords().stream().map(reward -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", reward.getId());
            map.put("inviterId", reward.getInviterId());
            map.put("inviteeId", reward.getInviteeId());
            map.put("reward", reward.getReward());
            map.put("rewardType", reward.getRewardType());
            map.put("remark", reward.getRemark());
            map.put("createTime", reward.getCreateTime());

            // 获取邀请人信息
            AppUser inviter = appUserService.getById(reward.getInviterId());
            if (inviter != null) {
                map.put("inviterNickname", inviter.getNickname());
                map.put("inviterAvatarUrl", inviter.getAvatarUrl());
            }

            // 获取被邀请人信息
            AppUser invitee = appUserService.getById(reward.getInviteeId());
            if (invitee != null) {
                map.put("inviteeNickname", invitee.getNickname());
                map.put("inviteeAvatarUrl", invitee.getAvatarUrl());
            }

            return map;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", result.getTotal());
        data.put("size", result.getSize());
        data.put("current", result.getCurrent());

        return Result.success(data);
    }
}
