package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.juxin.orin.entity.UserContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户签约记录Mapper
 */
@Mapper
public interface UserContractMapper extends BaseMapper<UserContract> {

    /**
     * 根据用户ID和服务商ID查询签约记录
     */
    @Select("SELECT * FROM user_contract WHERE user_id = #{userId} AND provider_id = #{providerId} LIMIT 1")
    UserContract findByUserAndProvider(@Param("userId") Long userId, @Param("providerId") String providerId);

    /**
     * 根据身份证号和服务商ID查询签约记录
     */
    @Select("SELECT * FROM user_contract WHERE id_card = #{idCard} AND provider_id = #{providerId} LIMIT 1")
    UserContract findByIdCardAndProvider(@Param("idCard") String idCard, @Param("providerId") String providerId);

    /**
     * 根据身份证号查询当前有效签约记录
     */
    @Select("SELECT * FROM user_contract WHERE id_card = #{idCard} AND provider_id = #{providerId} AND status = 1 ORDER BY update_time DESC, id DESC LIMIT 1")
    UserContract findActiveByIdCardAndProvider(@Param("idCard") String idCard, @Param("providerId") String providerId);

    /**
     * 根据身份证号查询最适合接收第三方状态同步的记录
     */
    @Select("SELECT * FROM user_contract WHERE id_card = #{idCard} AND provider_id = #{providerId} "
            + "ORDER BY CASE WHEN status IN (0, 3) THEN 0 WHEN status = 1 THEN 1 ELSE 2 END, update_time DESC, id DESC LIMIT 1")
    UserContract findSyncTargetByIdCardAndProvider(@Param("idCard") String idCard,
            @Param("providerId") String providerId);

    /**
     * 根据身份证号更新签约状态（用于解约）
     */
    @Update("UPDATE user_contract SET status = #{status}, update_time = NOW() WHERE id_card = #{idCard}")
    int updateStatusByIdCard(@Param("idCard") String idCard, @Param("status") int status);

    /**
     * 根据身份证号和服务商更新签约状态
     */
    @Update("UPDATE user_contract SET status = #{status}, update_time = NOW() WHERE id_card = #{idCard} AND provider_id = #{providerId}")
    int updateStatusByIdCardAndProvider(@Param("idCard") String idCard, @Param("providerId") String providerId,
            @Param("status") int status);
}
