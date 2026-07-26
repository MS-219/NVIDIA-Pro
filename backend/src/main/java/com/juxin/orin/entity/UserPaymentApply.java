package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收款信息变更申请
 */
@Data
@TableName("user_payment_apply")
public class UserPaymentApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 申请人ID
     */
    private Long userId;

    /**
     * 旧收款信息快照(JSON)
     */
    private String oldInfo;

    /**
     * 0-银行卡 1-支付宝
     */
    private Integer paymentType;

    /**
     * 新收款账号
     */
    private String newCardNo;

    /**
     * 状态: 0待审核, 1已通过, 2已驳回
     */
    private Integer status;

    /**
     * 驳回理由
     */
    private String rejectReason;

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
     * 用户昵称（非DB字段，联表查询或代码注入用）
     */
    @TableField(exist = false)
    private String nickname;
    
    /**
     * 用户旧卡号（非DB显示用）
     */
    @TableField(exist = false)
    private String oldCardNo;
}
