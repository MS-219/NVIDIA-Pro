package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备兑换订单实体
 */
@Data
@TableName("exchange_order")
public class ExchangeOrder {

    /** 订单状态常量 */
    public static final int STATUS_PENDING = 0;    // 待发货
    public static final int STATUS_SHIPPED = 1;    // 已发货
    public static final int STATUS_IN_TRANSIT = 2; // 运输中
    public static final int STATUS_RECEIVED = 3;   // 已到货
    public static final int STATUS_CANCELLED = 4;  // 已取消

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 商品ID */
    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 商品图片快照 */
    private String productImage;

    /** 数量 */
    private Integer quantity;

    /** 单价(元) */
    private BigDecimal unitPrice;

    /** 总价(元) */
    private BigDecimal totalPrice;

    /** 消耗算力值(展示用) */
    private Long hashrateCost;

    /** 下单时用户等级 */
    private Integer userLevel;

    /** 收货人 */
    private String receiverName;

    /** 收货电话 */
    private String receiverPhone;

    /** 完整收货地址 */
    private String receiverAddress;

    /** 订单状态: 0-待发货, 1-已发货, 2-运输中, 3-已到货, 4-已取消 */
    private Integer status;

    /** 快递公司 */
    private String expressCompany;

    /** 快递单号 */
    private String expressNo;

    /** 发货时间 */
    private LocalDateTime shipTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 用户备注 */
    private String remark;

    /** 管理员备注 */
    private String adminRemark;

    /** 邀请人ID */
    private Long inviterId;

    /** 邀请人等级 */
    private Integer inviterLevel;

    /** 邀请人分润金额(元) = 用户拿货价 - 邀请人拿货价 */
    private BigDecimal inviterProfit;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ========== 非数据库字段 ==========

    /** 用户昵称 */
    @TableField(exist = false)
    private String nickname;

    /** 用户头像 */
    @TableField(exist = false)
    private String avatarUrl;

    /**
     * 获取状态文本
     */
    public String getStatusText() {
        if (status == null) return "未知";
        switch (status) {
            case STATUS_PENDING: return "待发货";
            case STATUS_SHIPPED: return "已发货";
            case STATUS_IN_TRANSIT: return "运输中";
            case STATUS_RECEIVED: return "已到货";
            case STATUS_CANCELLED: return "已取消";
            default: return "未知";
        }
    }
}
