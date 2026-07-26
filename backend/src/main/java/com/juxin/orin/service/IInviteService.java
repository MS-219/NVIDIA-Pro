package com.juxin.orin.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 邀请服务接口
 */
public interface IInviteService {

    /**
     * 根据邀请码获取邀请人ID
     * 邀请码格式: JX + 6位用户ID (如 JX000002 表示用户ID为2)
     */
    Long getInviterIdByCode(String inviteCode);

    /**
     * 处理新用户注册的邀请关系绑定
     */
    void handleNewUserInvite(Long newUserId, String inviteCode);

    /**
     * 发放邀请奖励
     * 
     * @param inviterId  邀请人ID
     * @param inviteeId  被邀请人ID
     * @param rewardType 奖励类型
     * @param reward     奖励金额
     */
    void grantReward(Long inviterId, Long inviteeId, String rewardType, BigDecimal reward);

    /**
     * 发放邀请奖励（带设备ID）
     * 
     * @param inviterId  邀请人ID
     * @param inviteeId  被邀请人ID
     * @param rewardType 奖励类型
     * @param reward     奖励金额
     * @param deviceId   来源设备ID
     */
    void grantReward(Long inviterId, Long inviteeId, String rewardType, BigDecimal reward, Long deviceId);

    /**
     * 获取用户的邀请统计信息
     */
    Map<String, Object> getInviteStats(Long userId);

    /**
     * 获取用户的团队设备数
     * 
     * @param userId    用户ID
     * @param rootLevel 基准等级 (用于平级脱离判断，如果此参数为null，则使用userId自身的等级)
     */
    long getTeamDeviceCount(Long userId, Integer rootLevel);
}
