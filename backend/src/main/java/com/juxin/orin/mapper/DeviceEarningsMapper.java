package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.dto.DeviceEarningsDailyDto;
import com.juxin.orin.entity.DeviceEarnings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface DeviceEarningsMapper extends BaseMapper<DeviceEarnings> {

    /**
     * 统计用户某天的总收益
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM device_earnings WHERE user_id = #{userId} AND date = #{date}")
    BigDecimal sumByUserAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 统计用户累计总收益
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM device_earnings WHERE user_id = #{userId}")
    BigDecimal sumByUser(@Param("userId") Long userId);

    /**
     * 统计所有用户某天的总收益
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM device_earnings WHERE date = #{date}")
    BigDecimal sumByDate(@Param("date") LocalDate date);

    /**
     * 统计所有用户累计总收益
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM device_earnings")
    BigDecimal sumAll();

    @Select("SELECT COALESCE(SUM(amount), 0) FROM device_earnings WHERE device_id = #{deviceId}")
    BigDecimal sumByDevice(@Param("deviceId") Long deviceId);

    /**
     * 统计某台设备归属于指定用户期间产生的累计收益。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM device_earnings "
            + "WHERE device_id = #{deviceId} AND user_id = #{userId}")
    BigDecimal sumByDeviceAndUser(@Param("deviceId") Long deviceId, @Param("userId") Long userId);

    /**
     * 统计某设备某天的收益
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM device_earnings WHERE device_id = #{deviceId} AND date = #{date}")
    BigDecimal sumByDeviceAndDate(@Param("deviceId") Long deviceId, @Param("date") LocalDate date);

    /**
     * 统计某设备某天产生的收益记录数（即运行小时数）
     */
    @Select("SELECT COUNT(*) FROM device_earnings WHERE device_id = #{deviceId} AND date = #{date}")
    Integer countByDeviceAndDate(@Param("deviceId") Long deviceId, @Param("date") LocalDate date);

    /**
     * 统计用户本月总收益
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM device_earnings WHERE user_id = #{userId} AND date >= #{startDate} AND date <= #{endDate}")
    BigDecimal sumByUserAndDateRange(@Param("userId") Long userId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Select({
            "<script>",
            "SELECT",
            "  d.id AS deviceId,",
            "  d.sn AS sn,",
            "  d.bind_code AS bindCode,",
            "  d.status AS status,",
            "  d.last_heartbeat_time AS lastHeartbeatTime,",
            "  d.bind_time AS bindTime,",
            "  e.date AS date,",
            "  COALESCE(SUM(e.amount), 0) AS amount",
            "FROM device d",
            "JOIN device_earnings e ON e.device_id = d.id",
            "WHERE d.merchant_id = #{merchantId}",
            "<if test='startDate != null'>",
            "  AND e.date <![CDATA[>=]]> #{startDate}",
            "</if>",
            "<if test='endDate != null'>",
            "  AND e.date <![CDATA[<=]]> #{endDate}",
            "</if>",
            "GROUP BY d.id, d.sn, d.bind_code, d.status, d.last_heartbeat_time, d.bind_time, e.date",
            "ORDER BY e.date DESC",
            "</script>"
    })
    IPage<DeviceEarningsDailyDto> selectMerchantDeviceDailyEarnings(Page<?> page,
            @Param("merchantId") Long merchantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
