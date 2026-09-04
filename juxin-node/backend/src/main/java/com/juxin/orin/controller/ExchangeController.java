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
import java.math.RoundingMode;
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

    @Autowired
    private InviteLevelConfigService inviteLevelConfigService;

    /**
     * 获取上架商品列表（含当前用户等级对应的算力值价格）
     */
    @GetMapping("/products")
    public Map<String, Object> getProducts(
            @RequestParam(required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> result = new HashMap<>();
        try {
            userId = resolveOptionalViewer(userId, authorization, result);
            if (result.containsKey("code")) {
                return result;
            }
            // 获取用户等级
            AppUser user = userId != null ? appUserService.getById(userId) : null;
            int userLevel = (user != null && user.getLevel() != null) ? user.getLevel() : 0;

            // 读取算力兑换比例
            String rateStr = configService.getConfig("earnings.hashratePerYuan", "100");
            long hashrateRate = Long.parseLong(rateStr);

            // 查询上架商品
            List<ExchangeProduct> products = productService.lambdaQuery()
                    .eq(ExchangeProduct::getStatus, 1)
                    .orderByDesc(ExchangeProduct::getSortOrder)
                    .orderByDesc(ExchangeProduct::getCreateTime)
                    .list();

            // 只返回当前用户可见的价格，不向小程序暴露其他等级报价字段。
            List<Map<String, Object>> visibleProducts = products.stream()
                    .map(product -> toUserProductView(product, userLevel, hashrateRate))
                    .toList();

            // 读取用户可用算力值
            BigDecimal balance = (user != null && user.getBalance() != null) ? user.getBalance() : BigDecimal.ZERO;
            long availableHashrate = toHashrate(balance, hashrateRate);

            result.put("code", 200);
            Map<String, Object> data = new HashMap<>();
            data.put("products", visibleProducts);
            data.put("userLevel", userLevel);
            data.put("userLevelName", inviteLevelConfigService.getLevelName(userLevel));
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
    public Map<String, Object> getProductDetail(
            @PathVariable Long id,
            @RequestParam(required = false) Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> result = new HashMap<>();
        try {
            userId = resolveOptionalViewer(userId, authorization, result);
            if (result.containsKey("code")) {
                return result;
            }
            ExchangeProduct product = productService.getById(id);
            if (product == null) {
                result.put("code", 404);
                result.put("msg", "商品不存在");
                return result;
            }

            AppUser user = userId != null ? appUserService.getById(userId) : null;
            int userLevel = (user != null && user.getLevel() != null) ? user.getLevel() : 0;

            String rateStr = configService.getConfig("earnings.hashratePerYuan", "100");
            long hashrateRate = Long.parseLong(rateStr);

            BigDecimal userPrice = product.getPriceByLevel(userLevel);
            Map<String, Object> currentPrice = new LinkedHashMap<>();
            currentPrice.put("level", userLevel);
            currentPrice.put("levelName", inviteLevelConfigService.getLevelName(userLevel));
            currentPrice.put("price", userPrice);
            currentPrice.put("hashratePrice", toHashrate(userPrice, hashrateRate));

            BigDecimal balance = (user != null && user.getBalance() != null) ? user.getBalance() : BigDecimal.ZERO;
            long availableHashrate = toHashrate(balance, hashrateRate);

            Map<String, Object> data = new HashMap<>();
            data.put("product", toUserProductView(product, userLevel, hashrateRate));
            data.put("currentPrice", currentPrice);
            data.put("userLevel", userLevel);
            data.put("userLevelName", inviteLevelConfigService.getLevelName(userLevel));
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

    private Map<String, Object> toUserProductView(ExchangeProduct product, int userLevel, long hashrateRate) {
        BigDecimal userPrice = product.getPriceByLevel(userLevel);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", product.getId());
        view.put("name", product.getName());
        view.put("description", product.getDescription());
        view.put("imageUrl", product.getImageUrl());
        view.put("images", product.getImages());
        view.put("stock", product.getStock());
        view.put("status", product.getStatus());
        view.put("sortOrder", product.getSortOrder());
        view.put("userPrice", userPrice);
        view.put("userHashratePrice", toHashrate(userPrice, hashrateRate));
        return view;
    }

    /**
     * 下单兑换
     */
    @PostMapping("/order")
    public Map<String, Object> createOrder(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long requestedUserId = params.get("userId") == null ? null : Long.valueOf(params.get("userId").toString());
            Long userId = requireAuthenticatedUser(requestedUserId, authorization, result);
            if (userId == null) {
                return result;
            }
            if (params.get("productId") == null || params.get("addressId") == null) {
                result.put("code", 400);
                result.put("msg", "商品和收货地址不能为空");
                return result;
            }
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
    public Map<String, Object> getMyOrders(
            @RequestParam Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> result = new HashMap<>();
        try {
            userId = requireAuthenticatedUser(userId, authorization, result);
            if (userId == null) {
                return result;
            }
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
    public Map<String, Object> getOrderDetail(
            @PathVariable String orderNo,
            @RequestParam Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> result = new HashMap<>();
        try {
            userId = requireAuthenticatedUser(userId, authorization, result);
            if (userId == null) {
                return result;
            }
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
    public Map<String, Object> confirmReceive(
            @PathVariable String orderNo,
            @RequestParam Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> result = new HashMap<>();
        try {
            userId = requireAuthenticatedUser(userId, authorization, result);
            if (userId == null) {
                return result;
            }
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
    public Map<String, Object> getLogistics(
            @PathVariable("id") Long id,
            @RequestParam Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> result = new HashMap<>();
        try {
            userId = requireAuthenticatedUser(userId, authorization, result);
            if (userId == null) {
                return result;
            }
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

    private Long resolveOptionalViewer(
            Long requestedUserId,
            String authorization,
            Map<String, Object> result) {
        if ((authorization == null || authorization.isBlank()) && requestedUserId == null) {
            return null;
        }
        return requireAuthenticatedUser(requestedUserId, authorization, result);
    }

    private Long requireAuthenticatedUser(
            Long requestedUserId,
            String authorization,
            Map<String, Object> result) {
        String token = normalizeToken(authorization);
        if (token == null || !com.juxin.orin.util.JwtUtil.validateToken(token)
                || !"app".equals(com.juxin.orin.util.JwtUtil.getUserType(token))) {
            result.put("code", 401);
            result.put("msg", "未登录或登录已过期");
            return null;
        }
        Long tokenUserId = com.juxin.orin.util.JwtUtil.getUserId(token);
        if (tokenUserId == null) {
            result.put("code", 401);
            result.put("msg", "无效的登录信息");
            return null;
        }
        if (requestedUserId != null && !tokenUserId.equals(requestedUserId)) {
            result.put("code", 403);
            result.put("msg", "无权访问其他用户的兑换数据");
            return null;
        }
        return tokenUserId;
    }

    private String normalizeToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String token = authorization.trim();
        return token.startsWith("Bearer ") ? token.substring(7).trim() : token;
    }

    private long toHashrate(BigDecimal amount, long hashrateRate) {
        if (amount == null || hashrateRate <= 0) {
            return 0;
        }
        return amount.multiply(BigDecimal.valueOf(hashrateRate))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
