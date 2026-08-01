package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.juxin.orin.entity.ExchangeProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExchangeProductMapper extends BaseMapper<ExchangeProduct> {

    @Update("UPDATE exchange_product SET stock = stock + #{quantity}, update_time = NOW() WHERE id = #{productId} AND stock IS NOT NULL")
    int addStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("UPDATE exchange_product SET stock = stock - #{quantity}, update_time = NOW() "
            + "WHERE id = #{productId} AND status = 1 AND stock >= #{quantity}")
    int deductStockIfEnough(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
