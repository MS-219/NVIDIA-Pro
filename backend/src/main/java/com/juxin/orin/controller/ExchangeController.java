package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.ExchangeLogistics;
import com.juxin.orin.entity.ExchangeOrder;
import com.juxin.orin.entity.ExchangeProduct;
import com.juxin.orin.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 设备兑换 - 小程序端接口
 */
@RestController
@RequestMapping("/api/exchange")
public class ExchangeController {

    @Autowired
    private IExchangeProductService productService;

    @Autowired
    private IExchangeOrderService orderService;

    @Autowired
    private IExchangeLogisticsService logisticsService;

    @Autowired
    private IAppUserService appUserService;

    @Autowired
    private ISystemConfigService configService;

    /**
     * 获取上架商品列表（含当前用户等级对应的算力值价格）
     */
    @GetMapping("/products")
    public Map<String, Object> getProducts(@RequestParam(required = false) Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 获取用户等级
            AppUser user = userId != null ? appUserService.getById(userId) : null;
            int userLevel = (user != null && user.getLevel() != null) ? user.getLevel() : 0;

            // 读取算力兑换比例
            String rateStr = configService.getConfig("earnings.hashratePerYuan", "200");
            long hashrateRate = Long.parseLong(rateStr);

            // 查询上架商品
            List<ExchangeProduct> products = productService.lambdaQuery()
                    .eq(ExchangeProduct::getStatus, 1)
                    .orderByDesc(ExchangeProduct::getSortOrder)
                    .orderByDesc(ExchangeProduct::getCreateTime)
                    .list();

            // 填充当前用户等级对应的价格
            for (ExchangeProduct product : products) {
                BigDecimal userPrice = product.getPriceByLevel(userLevel);
                product.setUserPrice(userPrice);
                product.setUserHashratePrice(userPrice.longValue() * hashrateRate);
            }

            // 读取用户可用算力值
            BigDecimal balance = (user != null && user.getBalance() != null) ? user.getBalance() : BigDecimal.ZERO;
            long availableHashrate = balance.longValue() * hashrateRate;

            result.put("code", 200);
            Map<String, Object> data = new HashMap<>();
            data.put("products", products);
            data.put("userLevel", userLevel);
            data.put("hashrateRate", hashrateRate);
            data.put("availableHashrate", availableHashrate);
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取商品列表失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 商品详情
     */
    @GetMapping("/product/{id}")
    public Map<String, Object> getProductDetail(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ExchangeProduct product = productService.getById(id);
            if (product == null) {
                result.put("code", 404);
                result.put("msg", "商品不存在");
                return result;
            }

            AppUser user = userId != null ? appUserService.getById(userId) : null;
            int userLevel = (user != null && user.getLevel() != null) ? user.getLevel() : 0;

            String rateStr = configService.getConfig("earnings.hashratePerYuan", "200");
            long hashrateRate = Long.parseLong(rateStr);

            // 设置当前用户价格
            BigDecimal userPrice = product.getPriceByLevel(userLevel);
            product.setUserPrice(userPrice);
            product.setUserHashratePrice(userPrice.longValue() * hashrateRate);

            // 构建所有等级价格列表
            List<Map<String, Object>> allPrices = new ArrayList<>();
            String[] levelNames = {"", "会员", "社区", "县级", "市级", "联创"};
            for (int i = 1; i <= 5; i++) {
                Map<String, Object> priceInfo = new HashMap<>();
                priceInfo.put("level", i);
                priceInfo.put("levelName", levelNames[i]);
                BigDecimal price = product.getPriceByLevel(i);
                priceInfo.put("price", price);
                priceInfo.put("hashratePrice", price.longValue() * hashrateRate);
                priceInfo.put("isCurrent", i == userLevel);
                allPrices.add(priceInfo);
            }

            BigDecimal balance = (user != null && user.getBalance() != null) ? user.getBalance() : BigDecimal.ZERO;
            long availableHashrate = balance.longValue() * hashrateRate;

            Map<String, Object> data = new HashMap<>();
            data.put("product", product);
            data.put("allPrices", allPrices);
            data.put("userLevel", userLevel);
            data.put("hashrateRate", hashrateRate);
            data.put("availableHashrate", availableHashrate);

            result.put("code", 200);
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取商品详情失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 下单兑换
     */
    @PostMapping("/order")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = Long.valueOf(params.get("userId").toString());
            Long productId = Long.valueOf(params.get("productId").toString());
            Long addressId = Long.valueOf(params.get("addressId").toString());
            Integer quantity = params.containsKey("quantity") ? Integer.valueOf(params.get("quantity").toString()) : 1;
            String remark = params.containsKey("remark") ? params.get("remark").toString() : null;

            String orderNo = orderService.createOrder(userId, productId, addressId, quantity, remark);

            result.put("code", 200);
            result.put("msg", "兑换成功");
            result.put("data", Map.of("orderNo", orderNo));
        } catch (RuntimeException e) {
            result.put("code", 400);
            result.put("msg", e.getMessage());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "下单失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 我的订单列表
     */
    @GetMapping("/orders")
    public Map<String, Object> getMyOrders(@RequestParam Long userId,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            LambdaQueryWrapper<ExchangeOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ExchangeOrder::getUserId, userId);
            if (status != null) {
                wrapper.eq(ExchangeOrder::getStatus, status);
            }
            wrapper.orderByDesc(ExchangeOrder::getCreateTime);

            var pageResult = orderService.page(
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                    wrapper
            );

            result.put("code", 200);
            result.put("data", Map.of(
                    "records", pageResult.getRecords(),
                    "total", pageResult.getTotal(),
                    "pages", pageResult.getPages(),
                    "current", pageResult.getCurrent()
            ));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取订单列表失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 订单详情 + 物流跟踪
     */
    @GetMapping("/order/{orderNo}")
    public Map<String, Object> getOrderDetail(@PathVariable String orderNo, @RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ExchangeOrder order = orderService.lambdaQuery()
                    .eq(ExchangeOrder::getOrderNo, orderNo)
                    .eq(ExchangeOrder::getUserId, userId)
                    .one();

            if (order == null) {
                result.put("code", 404);
                result.put("msg", "订单不存在");
                return result;
            }

            // 查询物流记录
            List<ExchangeLogistics> logistics = logisticsService.lambdaQuery()
                    .eq(ExchangeLogistics::getOrderId, order.getId())
                    .orderByDesc(ExchangeLogistics::getCreateTime)
                    .list();

            Map<String, Object> data = new HashMap<>();
            data.put("order", order);
            data.put("logistics", logistics);

            result.put("code", 200);
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取订单详情失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 确认收货
     */
    @PostMapping("/order/{orderNo}/confirm")
    public Map<String, Object> confirmReceive(@PathVariable String orderNo, @RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ExchangeOrder order = orderService.lambdaQuery()
                    .eq(ExchangeOrder::getOrderNo, orderNo)
                    .eq(ExchangeOrder::getUserId, userId)
                    .one();

            if (order == null) {
                result.put("code", 404);
                result.put("msg", "订单不存在");
                return result;
            }

            if (order.getStatus() == ExchangeOrder.STATUS_RECEIVED) {
                result.put("code", 400);
                result.put("msg", "订单已确认收货");
                return result;
            }

            if (order.getStatus() != ExchangeOrder.STATUS_SHIPPED && order.getStatus() != ExchangeOrder.STATUS_IN_TRANSIT) {
                result.put("code", 400);
                result.put("msg", "当前订单状态不支持确认收货");
                return result;
            }

            orderService.lambdaUpdate()
                    .set(ExchangeOrder::getStatus, ExchangeOrder.STATUS_RECEIVED)
                    .set(ExchangeOrder::getReceiveTime, java.time.LocalDateTime.now())
                    .eq(ExchangeOrder::getId, order.getId())
                    .update();

            // 写入物流记录
            ExchangeLogistics log = new ExchangeLogistics();
            log.setOrderId(order.getId());
            log.setStatus(ExchangeOrder.STATUS_RECEIVED);
            log.setDescription("用户已确认收货");
            log.setOperator("用户");
            log.setCreateTime(java.time.LocalDateTime.now());
            logisticsService.save(log);

            result.put("code", 200);
            result.put("msg", "确认收货成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "操作失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 查询订单物流信息（对接阿里云）
     */
    @GetMapping("/orders/{id}/logistics")
    public Map<String, Object> getLogistics(@PathVariable("id") Long id, @RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> logistics = orderService.getLogisticsInfo(userId, id);
            result.put("code", 200);
            result.put("msg", "success");
            result.put("data", logistics);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
        }
        return result;
    }
}
