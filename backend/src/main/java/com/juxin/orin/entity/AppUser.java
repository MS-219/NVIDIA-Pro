package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 小程序用户实体
 */
@Data
@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 微信 OpenID */
    private String openid;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 手机号 */
    private String phone;

    /** 用户余额 */
    private BigDecimal balance;

    /** 剩余配额（用于AI创作） */
    private Integer quota;

    /** 邀请人ID */
    private Long inviterId;

    /** 用户等级: 0-普通用户, 1-一级, 2-二级, 3-三级, 4-四级 */
    private Integer level;

    /** 等级是否由后台手动设置（手动设置优先级高于自动升级） */
    private Boolean levelManual;

    /** 微信收款码 */
    private String wxQrCode;

    /** 支付宝收款码 */
    private String aliQrCode;

    /** 银行名称 */
    private String bankName;

    /** 银行卡号 */
    private String bankCardNo;

    /** 持卡人姓名 */
    private String bankHolderName;

    /** 身份证号 */
    private String idCard;

    /** 身份证人像面照片URL */
    private String idCardFront;

    /** 身份证国徽面照片URL */
    private String idCardBack;

    /** 支付宝账号 */
    private String alipayAccount;

    /** 是否禁止提现 */
    private Boolean withdrawDisabled;

    /** 用户类型: personal-个人用户, company-公司用户 */
    private String userType;

    /** 后台备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除: 0-正常, 1-回收站 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    /** 删除时间 */
    private LocalDateTime deletedAt;

    /** 所属商户ID */
    private Long merchantId;

    /** 外部系统用户ID */
    private String externalUserId;

    // ========== 非数据库字段 ==========

    /** 绑定的设备数量 */
    @TableField(exist = false)
    private Integer deviceCount;

    /** 创作任务数量 */
    @TableField(exist = false)
    private Integer taskCount;

    /** 邀请人昵称 */
    @TableField(exist = false)
    private String inviterNickname;

    /** 邀请人头像 */
    @TableField(exist = false)
    private String inviterAvatarUrl;

    /** 签约状态: 0-待签约, 1-已签约, 2-签约失败, 3-签约中 */
    @TableField(exist = false)
    private Integer contractStatus;

    /** 签约状态描述 */
    @TableField(exist = false)
    private String contractStatusText;
}
