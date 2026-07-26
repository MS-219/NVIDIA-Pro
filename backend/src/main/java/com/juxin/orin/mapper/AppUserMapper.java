package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.juxin.orin.entity.AppUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    @Update("UPDATE app_user SET balance = COALESCE(balance, 0) + #{amount} "
            + "WHERE id = #{userId} AND deleted = 0")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Select({
            "<script>",
            "SELECT * FROM app_user",
            "WHERE deleted = 1 AND merchant_id IS NULL",
            "<if test='keyword != null and keyword != &quot;&quot;'>",
            "AND (nickname LIKE CONCAT('%', #{keyword}, '%')",
            "OR phone LIKE CONCAT('%', #{keyword}, '%')",
            "OR openid LIKE CONCAT('%', #{keyword}, '%')",
            "OR CAST(id AS CHAR) LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY deleted_at DESC, create_time DESC",
            "</script>"
    })
    IPage<AppUser> selectRecyclePage(Page<AppUser> page, @Param("keyword") String keyword);

    @Update("UPDATE app_user SET deleted = 1, deleted_at = NOW() "
            + "WHERE id = #{userId} AND deleted = 0 AND merchant_id IS NULL")
    int moveToRecycleBin(@Param("userId") Long userId);

    @Update("UPDATE app_user SET deleted = 0, deleted_at = NULL "
            + "WHERE id = #{userId} AND deleted = 1 AND merchant_id IS NULL")
    int restoreFromRecycleBin(@Param("userId") Long userId);

    @Select("SELECT id FROM app_user WHERE deleted = 1 AND merchant_id IS NULL")
    List<Long> selectRecycleUserIds();

    @Select("SELECT COUNT(*) FROM app_user WHERE id = #{userId}")
    int countByIdIncludingDeleted(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM app_user WHERE openid = #{openid} AND deleted = 1")
    int countDeletedByOpenid(@Param("openid") String openid);

    @Delete({
            "<script>",
            "DELETE FROM app_user WHERE deleted = 1 AND merchant_id IS NULL AND id IN",
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int permanentlyDelete(@Param("userIds") List<Long> userIds);
}
