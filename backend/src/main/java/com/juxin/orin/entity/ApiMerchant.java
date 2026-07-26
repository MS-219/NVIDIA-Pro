package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 接口商户实体
 */
@Data
@TableName("api_merchant")
public class ApiMerchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * AppID
     */
    private String appId;

    /**
     * AppSecret
     */
    private String appSecret;

    /**
     * 可见功能权限, 逗号分隔 (例如: text-to-video,image-to-video)
     */
    private String permissions;

    /**
     * 状态 0:禁用 1:启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 有效期至
     */
    private LocalDateTime expireTime;

    /**
     * 账户余额 (佣金)
     */
    private BigDecimal balance;

    /**
     * 商户等级 (关联系统的分润 Rate)
     */
    private Integer level;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 绑定的平台用户ID（作为资金账户）
     */
    private Long bindUserId;

    /**
     * 设备总数 (非数据库字段)
     */
    @TableField(exist = false)
    private Long deviceCount;

    /**
     * 绑定账号昵称（非数据库字段）
     */
    @TableField(exist = false)
    private String bindUserNickname;

    /**
     * 绑定账号手机号（非数据库字段）
     */
    @TableField(exist = false)
    private String bindUserPhone;
}
