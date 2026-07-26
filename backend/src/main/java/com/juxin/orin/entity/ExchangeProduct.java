package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 兑换商品实体
 */
@Data
@TableName("exchange_product")
public class ExchangeProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品描述 */
    private String description;

    /** 商品主图 */
    private String imageUrl;

    /** 商品图片列表(JSON数组) */
    private String images;

    /** 基础价格(元) */
    private BigDecimal basePrice;

    /** 会员价(元) - level 1 */
    private BigDecimal priceLevel1;

    /** 社区价(元) - level 2 */
    private BigDecimal priceLevel2;

    /** 县级价(元) - level 3 */
    private BigDecimal priceLevel3;

    /** 市级价(元) - level 4 */
    private BigDecimal priceLevel4;

    /** 联创价(元) - level 5 */
    private BigDecimal priceLevel5;

    /** 库存数量 */
    private Integer stock;

    /** 状态: 0-下架, 1-上架 */
    private Integer status;

    /** 排序权重 */
    private Integer sortOrder;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ========== 非数据库字段 ==========

    /** 当前用户等级对应的价格(元) */
    @TableField(exist = false)
    private BigDecimal userPrice;

    /** 当前用户等级对应的算力值价格 */
    @TableField(exist = false)
    private Long userHashratePrice;

    /**
     * 根据用户等级获取对应价格
     */
    public BigDecimal getPriceByLevel(int level) {
        switch (level) {
            case 1: return priceLevel1 != null ? priceLevel1 : basePrice;
            case 2: return priceLevel2 != null ? priceLevel2 : basePrice;
            case 3: return priceLevel3 != null ? priceLevel3 : basePrice;
            case 4: return priceLevel4 != null ? priceLevel4 : basePrice;
            case 5: return priceLevel5 != null ? priceLevel5 : basePrice;
            default: return basePrice;
        }
    }
}
