package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备收益记录实体
 */
@Data
@TableName("device_earnings")
public class DeviceEarnings {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    private Long deviceId;

    /** 用户ID */
    private Long userId;

    /** 收益金额 */
    private BigDecimal amount;

    /** 收益日期 */
    private LocalDate date;

    /** 创建时间 */
    private LocalDateTime createTime;

    // ========== 非数据库字段 ==========

    /** 用户昵称 */
    @TableField(exist = false)
    private String nickname;

    /** 用户头像 */
    @TableField(exist = false)
    private String avatarUrl;
}
