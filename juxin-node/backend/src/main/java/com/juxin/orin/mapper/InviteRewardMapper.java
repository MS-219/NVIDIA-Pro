package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.juxin.orin.dto.InviteeRewardSummary;
import com.juxin.orin.entity.InviteReward;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.math.BigDecimal;

/**
 * 邀请奖励 Mapper
 */
@Mapper
public interface InviteRewardMapper extends BaseMapper<InviteReward> {
    @Select("SELECT invitee_id AS inviteeId, COALESCE(SUM(reward), 0) AS totalReward "
            + "FROM invite_reward WHERE inviter_id = #{inviterId} GROUP BY invitee_id")
    List<InviteeRewardSummary> sumRewardByInvitee(@Param("inviterId") Long inviterId);

    @Select("SELECT COALESCE(SUM(reward), 0) FROM invite_reward "
            + "WHERE inviter_id = #{inviterId} AND reward_type = 'earnings'")
    BigDecimal sumEarningsRewardByInviter(@Param("inviterId") Long inviterId);
}
