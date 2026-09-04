package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.juxin.orin.entity.Withdraw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface WithdrawMapper extends BaseMapper<Withdraw> {

    /**
     * 统计用户已提现总额
     */
    @Select("SELECT IFNULL(SUM(amount), 0) FROM withdraw WHERE user_id = #{userId} AND status = 3")
    BigDecimal sumWithdrawnByUser(@Param("userId") Long userId);

    /**
     * 统计用户待审核提现总额
     */
    @Select("SELECT IFNULL(SUM(amount), 0) FROM withdraw WHERE user_id = #{userId} AND status IN (0, 1)")
    BigDecimal sumPendingByUser(@Param("userId") Long userId);
}
