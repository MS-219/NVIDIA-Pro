package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户签约记录实体
 */
@Data
@TableName("user_contract")
public class UserContract {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 真实姓名 */
    private String realName;

    /** 身份证号 */
    private String idCard;

    /** 手机号 */
    private String mobile;

    /** 银行卡号 */
    private String bankCardNo;

    /** 支付宝账号 */
    private String alipayAccount;

    /** 签约状态: 0-待签约 1-已签约 2-签约失败 3-签约中 5-已解约 */
    private Integer status;

    /** 服务商ID */
    private String providerId;

    /** 签约方式: 0-银行卡 1-支付宝 2-微信 */
    private Integer paymentType;

    /** 签约成功时间 */
    private LocalDateTime contractTime;

    /** 失败原因 */
    private String failReason;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ========== 签约状态常量 ==========
    public static final int STATUS_PENDING = 0; // 待签约
    public static final int STATUS_SUCCESS = 1; // 已签约
    public static final int STATUS_FAILED = 2; // 签约失败 (注：文档中是4,但为了和现有逻辑统一用2)
    public static final int STATUS_PROCESSING = 3; // 签约中
    public static final int STATUS_CANCELLED = 5; // 已解约

    // ========== 签约方式常量 ==========
    public static final int PAY_TYPE_BANK = 0; // 银行卡
    public static final int PAY_TYPE_ALIPAY = 1; // 支付宝
    public static final int PAY_TYPE_WECHAT = 2; // 微信
}
