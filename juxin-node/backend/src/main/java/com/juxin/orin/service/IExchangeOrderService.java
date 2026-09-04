package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.ExchangeOrder;

import java.util.Map;

public interface IExchangeOrderService extends IService<ExchangeOrder> {

    /**
     * 创建兑换订单
     * @param userId 用户ID
     * @param productId 商品ID
     * @param addressId 收货地址ID
     * @param quantity 数量
     * @param remark 备注
     * @return 订单号
     */
    String createOrder(Long userId, Long productId, Long addressId, Integer quantity, String remark);

    /**
     * 管理后台退回兑换订单，并返还用户余额/库存。
     *
     * @param orderId 订单ID
     * @param adminRemark 管理员备注
     */
    void cancelOrderByAdmin(Long orderId, String adminRemark);

    /**
     * 获取兑换统计
     */
    Map<String, Object> getStatistics();

    /**
     * 查询订单物流信息
     * @param userId 用户ID
     * @param orderId 订单ID
     * @return 物流信息
     */
    Map<String, Object> getLogisticsInfo(Long userId, Long orderId);
}
