package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 邀请奖励记录实体
 */
@Data
@TableName("invite_reward")
public class InviteReward {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 邀请人ID */
    private Long inviterId;

    /** 被邀请人ID */
    private Long inviteeId;

    /** 奖励金额 */
    private BigDecimal reward;

    /** 奖励类型：register-注册奖励, device-设备绑定奖励, earnings-收益分成 */
    private String rewardType;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 来源设备ID（收益分成时记录产生收益的设备） */
    private Long deviceId;

    /** 备注 */
    private String remark;
}
