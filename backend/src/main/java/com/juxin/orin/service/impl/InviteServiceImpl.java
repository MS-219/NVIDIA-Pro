package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.juxin.orin.dto.InviteeRewardSummary;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.InviteReward;
import com.juxin.orin.mapper.InviteRewardMapper;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IInviteService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.ISystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.juxin.orin.entity.Device;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 邀请服务实现
 */
@Slf4j
@Service
public class InviteServiceImpl implements IInviteService {

    @Autowired
    private IAppUserService appUserService;

    @Autowired
    private InviteRewardMapper inviteRewardMapper;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private ISystemConfigService configService;

    @Override
    public Long getInviterIdByCode(String inviteCode) {
        if (inviteCode == null || inviteCode.length() < 3) {
            return null;
        }

        // 邀请码格式: JX + 用户ID (去除前导零)
        String code = inviteCode.toUpperCase().trim();
        if (!code.startsWith("JX")) {
            return null;
        }

        try {
            String idPart = code.substring(2).replaceFirst("^0+", "");
            if (idPart.isEmpty()) {
                idPart = "0";
            }
            Long inviterId = Long.parseLong(idPart);

            // 验证邀请人是否存在
            AppUser inviter = appUserService.getById(inviterId);
            if (inviter == null) {
                log.warn("邀请人不存在: code={}, inviterId={}", inviteCode, inviterId);
                return null;
            }

            return inviterId;
        } catch (NumberFormatException e) {
            log.warn("无效的邀请码格式: {}", inviteCode);
            return null;
        }
    }

    @Override
    @Transactional
    public void handleNewUserInvite(Long newUserId, String inviteCode) {
        if (inviteCode == null || inviteCode.isEmpty()) {
            return;
        }

        Long inviterId = getInviterIdByCode(inviteCode);
        if (inviterId == null) {
            log.warn("无效邀请码，无法绑定邀请关系: newUserId={}, inviteCode={}", newUserId, inviteCode);
            return;
        }

        // 不能自己邀请自己
        if (inviterId.equals(newUserId)) {
            log.warn("不能自己邀请自己: userId={}", newUserId);
            return;
        }

        // 更新新用户的邀请人ID
        AppUser newUser = appUserService.getById(newUserId);
        if (newUser == null) {
            log.error("新用户不存在: {}", newUserId);
            return;
        }

        // 如果已经有邀请人，不再重复绑定
        if (newUser.getInviterId() != null) {
            log.info("用户已有邀请人，不再重复绑定: userId={}, existingInviterId={}", newUserId, newUser.getInviterId());
            return;
        }

        newUser.setInviterId(inviterId);
        appUserService.updateById(newUser);
        log.info("成功绑定邀请关系: newUserId={}, inviterId={}", newUserId, inviterId);
    }

    @Override
    @Transactional
    public void grantReward(Long inviterId, Long inviteeId, String rewardType, BigDecimal reward) {
        grantReward(inviterId, inviteeId, rewardType, reward, null);
    }

    @Override
    @Transactional
    public void grantReward(Long inviterId, Long inviteeId, String rewardType, BigDecimal reward, Long deviceId) {
        if (reward == null || reward.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 创建奖励记录
        InviteReward record = new InviteReward();
        record.setInviterId(inviterId);
        record.setInviteeId(inviteeId);
        record.setReward(reward);
        record.setRewardType(rewardType);
        record.setCreateTime(LocalDateTime.now());
        record.setDeviceId(deviceId);

        String rewardTypeText = "register".equals(rewardType) ? "注册奖励"
                : "device".equals(rewardType) ? "设备绑定奖励" : "earnings".equals(rewardType) ? "收益分成" : "其他奖励";
        record.setRemark(rewardTypeText);

        inviteRewardMapper.insert(record);

        // 增加邀请人余额
        AppUser inviter = appUserService.getById(inviterId);
        if (inviter != null) {
            BigDecimal currentBalance = inviter.getBalance() != null ? inviter.getBalance() : BigDecimal.ZERO;
            inviter.setBalance(currentBalance.add(reward));

            // 同步更新聚芯算力值 (配额)
            int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));
            int quotaAdd = reward.multiply(new java.math.BigDecimal(hashrateRate)).intValue();
            inviter.setQuota((inviter.getQuota() != null ? inviter.getQuota() : 0) + quotaAdd);

            appUserService.updateById(inviter);
            log.info("发放邀请奖励: inviterId={}, inviteeId={}, type={}, reward={}, deviceId={}, newBalance={}, quotaAdd={}",
                    inviterId, inviteeId, rewardType, reward, deviceId, inviter.getBalance(), quotaAdd);
        }
    }

    @Override
    public Map<String, Object> getInviteStats(Long userId) {
        Map<String, Object> result = new HashMap<>();

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return result;
        }

        // 1. 获取用户基本邀请数据
        result.put("level", user.getLevel() != null ? user.getLevel() : 0);

        // 查询被该用户邀请的用户列表
        LambdaQueryWrapper<AppUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(AppUser::getInviterId, userId)
                .orderByDesc(AppUser::getCreateTime);
        List<AppUser> invitedUsers = appUserService.list(userWrapper);

        // 统计邀请人数
        result.put("inviteCount", invitedUsers.size());

        List<InviteeRewardSummary> rewardSummaries = inviteRewardMapper.sumRewardByInvitee(userId);
        BigDecimal totalReward = rewardSummaries.stream()
                .map(summary -> summary.getTotalReward() != null ? summary.getTotalReward() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, BigDecimal> rewardByInvitee = rewardSummaries.stream()
                .filter(summary -> summary.getInviteeId() != null)
                .collect(Collectors.toMap(
                        InviteeRewardSummary::getInviteeId,
                        summary -> summary.getTotalReward() != null ? summary.getTotalReward() : BigDecimal.ZERO,
                        BigDecimal::add));
        result.put("totalReward", totalReward.setScale(2, java.math.RoundingMode.HALF_UP).toString());

        // 2. 统计邀请网络总设备数 (递归统计，支持平级脱离)

        // 2.1 获取用户自己的设备数
        long ownDevices = deviceService.lambdaQuery()
                .eq(Device::getUserId, userId)
                .count();
        long ownOnlineDevices = deviceService.lambdaQuery()
                .eq(Device::getUserId, userId)
                .and(w -> w.eq(Device::getStatus, 1).or().eq(Device::getType, 1))
                .count();

        // 2.2 获取团队（下级）设备数 - 用于展示，不受等级脱离影响
        int teamTotalDevices = (int) getTeamDeviceCountRecursive(userId, Integer.MAX_VALUE, false);
        int teamOnlineDevices = (int) getTeamDeviceCountRecursive(userId, Integer.MAX_VALUE, true);

        // 2.3 合并总数 (保持 totalDeviceCount 为总数用于等级计算)
        result.put("totalDeviceCount", (int) ownDevices + teamTotalDevices);
        result.put("teamDeviceCount", teamTotalDevices);
        // 新增字段：仅团队在线设备数，用于前端展示“邀请成果-在线设备”
        result.put("teamOnlineDeviceCount", teamOnlineDevices);
        // 兼容旧字段（如果其他地方用了这个作为总在线）
        result.put("onlineDeviceCount", (int) ownOnlineDevices + teamOnlineDevices);

        // 3. 获取等级配置（名称、阈值、比例）
        List<Map<String, Object>> levels = new ArrayList<>();
        String[] defaults = { "普通", "会员", "社区", "县级", "市级", "联创" };
        String[] defaultRates = { "0.0", "0.7", "0.8", "0.85", "0.9", "0.95" };
        String[] defaultThresholds = { "0", "1", "100", "300", "1000", "3000" };

        for (int i = 0; i <= 5; i++) {
            Map<String, Object> lv = new HashMap<>();
            lv.put("index", i);
            if (i == 0) {
                lv.put("name", defaults[0]);
                lv.put("rate", "0.1"); // 普通用户默认 10%
                lv.put("threshold", 0);
            } else {
                lv.put("name", configService.getConfig("invite.level" + i + ".name", defaults[i]));
                lv.put("rate", configService.getConfig("invite.level" + i + ".rate", defaultRates[i]));
                lv.put("threshold", Integer
                        .parseInt(configService.getConfig("invite.level" + i + ".threshold", defaultThresholds[i])));
            }
            levels.add(lv);
        }
        result.put("levelConfigs", levels);

        // 4. 构建邀请用户列表 (显示每个受邀者及其子团队贡献的设备总数，含平级脱离)
        List<Map<String, Object>> userList = new ArrayList<>();
        for (AppUser invitee : invitedUsers) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", invitee.getId());
            userInfo.put("nickname", invitee.getNickname());
            userInfo.put("avatarUrl", invitee.getAvatarUrl());
            userInfo.put("registerTime", invitee.getCreateTime());
            userInfo.put("level", invitee.getLevel() != null ? invitee.getLevel() : 0);

            // 修改：始终显示被邀请人实际持有的设备数（含团队），不受等级脱离影响，方便直观查看
            long inviteeOwnDevices = deviceService.lambdaQuery()
                    .eq(Device::getUserId, invitee.getId())
                    .count();
            // 统计该或者的下级团队（传入 MAX_VALUE 确保不因等级脱离而隐藏显示）
            long subTeamDevices = getTeamDeviceCountRecursive(invitee.getId(), Integer.MAX_VALUE, false);

            userInfo.put("deviceCount", inviteeOwnDevices + subTeamDevices);

            // 另外计算贡献设备数（如需显示可增加字段，暂不破坏原有结构）

            BigDecimal userTotalReward = rewardByInvitee.getOrDefault(invitee.getId(), BigDecimal.ZERO);
            userInfo.put("reward", userTotalReward.setScale(2, java.math.RoundingMode.HALF_UP));

            userList.add(userInfo);
        }
        result.put("invitedUsers", userList);

        return result;
    }

    @Override
    public long getTeamDeviceCount(Long userId, Integer rootLevel) {
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return 0;
        }

        int currentLevel = user.getLevel() != null ? user.getLevel() : 0;
        int limitLevel = rootLevel != null ? rootLevel : currentLevel;

        // 如果是为上级统计，且当前用户已经平级或更高，则整个分支脱离
        if (rootLevel != null && currentLevel >= rootLevel) {
            return 0;
        }

        long own = deviceService.lambdaQuery().eq(Device::getUserId, userId).count();
        long sub = getTeamDeviceCountRecursive(userId, limitLevel, false);
        return own + sub;
    }

    /**
     * 递归统计团队设备数
     * 规则：当下级等级 >= rootLevel 时，该分支（下级及其所有后代）不计入统计
     */
    private long getTeamDeviceCountRecursive(Long userId, int rootLevel, boolean onlyOnline) {
        long count = 0;

        // 获取所有直属下级
        List<AppUser> invitees = appUserService.lambdaQuery()
                .eq(AppUser::getInviterId, userId)
                .list();

        if (invitees == null || invitees.isEmpty()) {
            return 0;
        }

        for (AppUser invitee : invitees) {
            // 平级或更高等级脱离规则：如果下级等级 >= rootLevel，则不统计该分支
            int inviteeLevel = invitee.getLevel() != null ? invitee.getLevel() : 0;
            if (inviteeLevel >= rootLevel) {
                continue;
            }

            // 1. 统计该受邀者的设备数
            long inviteeDevices = deviceService.lambdaQuery()
                    .eq(Device::getUserId, invitee.getId())
                    .and(onlyOnline, w -> w.eq(Device::getStatus, 1).or().eq(Device::getType, 1))
                    .count();
            count += inviteeDevices;

            // 2. 递归统计该受邀者的下级设备数
            count += getTeamDeviceCountRecursive(invitee.getId(), rootLevel, onlyOnline);
        }

        return count;
    }
}
