package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流跟踪记录实体
 */
@Data
@TableName("exchange_logistics")
public class ExchangeLogistics {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 状态: 0-待发货, 1-已发货, 2-运输中, 3-已到货 */
    private Integer status;

    /** 物流描述 */
    private String description;

    /** 操作人 */
    private String operator;

    private LocalDateTime createTime;
}
