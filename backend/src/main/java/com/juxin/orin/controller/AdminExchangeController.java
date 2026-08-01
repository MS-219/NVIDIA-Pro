package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.ExchangeLogistics;
import com.juxin.orin.entity.ExchangeOrder;
import com.juxin.orin.entity.ExchangeProduct;
import com.juxin.orin.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备兑换 - 管理后台接口
 */
@RestController
@RequestMapping("/api/admin/exchange")
public class AdminExchangeController {

    @Autowired
    private IExchangeProductService productService;

    @Autowired
    private IExchangeOrderService orderService;

    @Autowired
    private IExchangeLogisticsService logisticsService;

    @Autowired
    private IAppUserService appUserService;

    // ========== 商品管理 ==========

    /**
     * 商品列表（含下架）
     */
    @GetMapping("/products")
    public Map<String, Object> getProducts(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status) {
        Map<String, Object> result = new HashMap<>();
        try {
            LambdaQueryWrapper<ExchangeProduct> wrapper = new LambdaQueryWrapper<>();
            if (keyword != null && !keyword.trim().isEmpty()) {
                wrapper.like(ExchangeProduct::getName, keyword.trim());
            }
            if (status != null) {
                wrapper.eq(ExchangeProduct::getStatus, status);
            }
            wrapper.orderByDesc(ExchangeProduct::getSortOrder)
                    .orderByDesc(ExchangeProduct::getCreateTime);

            Page<ExchangeProduct> pageResult = productService.page(new Page<>(page, size), wrapper);

            result.put("code", 200);
            result.put("data", Map.of(
                    "records", pageResult.getRecords(),
                    "total", pageResult.getTotal(),
                    "pages", pageResult.getPages(),
                    "current", pageResult.getCurrent()
            ));
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取商品列表失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 新增/编辑商品
     */
    @PostMapping("/product")
    public Map<String, Object> saveProduct(@RequestBody ExchangeProduct product) {
        Map<String, Object> result = new HashMap<>();
        try {
            String validationError = validateProduct(product);
            if (validationError != null) {
                result.put("code", 400);
                result.put("msg", validationError);
                return result;
            }

            if (product.getId() != null) {
                if (productService.getById(product.getId()) == null) {
                    result.put("code", 404);
                    result.put("msg", "商品不存在");
                    return result;
                }
                product.setUpdateTime(LocalDateTime.now());
                if (!productService.updateById(product)) {
                    throw new IllegalStateException("商品数据已变化，请刷新后重试");
                }
            } else {
                product.setCreateTime(LocalDateTime.now());
                product.setUpdateTime(LocalDateTime.now());
                productService.save(product);
            }

            result.put("code", 200);
            result.put("msg", "保存成功");
            result.put("data", product);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "保存失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 上下架切换
     */
    @PostMapping("/product/{id}/toggle")
    public Map<String, Object> toggleProduct(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            ExchangeProduct product = productService.getById(id);
            if (product == null) {
                result.put("code", 404);
                result.put("msg", "商品不存在");
                return result;
            }

            int newStatus = (product.getStatus() != null && product.getStatus() == 1) ? 0 : 1;
            productService.lambdaUpdate()
                    .set(ExchangeProduct::getStatus, newStatus)
                    .eq(ExchangeProduct::getId, id)
                    .update();

            result.put("code", 200);
            result.put("msg", newStatus == 1 ? "上架成功" : "下架成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "操作失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/product/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (productService.getById(id) == null) {
                result.put("code", 404);
                result.put("msg", "商品不存在");
                return result;
            }
            long activeOrders = orderService.lambdaQuery()
                    .eq(ExchangeOrder::getProductId, id)
                    .in(ExchangeOrder::getStatus,
                            ExchangeOrder.STATUS_PENDING,
                            ExchangeOrder.STATUS_SHIPPED,
                            ExchangeOrder.STATUS_IN_TRANSIT)
                    .count();
            if (activeOrders > 0) {
                result.put("code", 400);
                result.put("msg", "该商品存在未完成订单，请先下架，不能删除");
                return result;
            }
            productService.removeById(id);
            result.put("code", 200);
            result.put("msg", "删除成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "删除失败: " + e.getMessage());
        }
        return result;
    }

    // ========== 订单管理 ==========

    /**
     * 订单详情及物流记录
     */
    @GetMapping("/order/{id}")
    public Map<String, Object> getOrderDetail(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            ExchangeOrder order = orderService.getById(id);
            if (order == null) {
                result.put("code", 404);
                result.put("msg", "订单不存在");
                return result;
            }
            AppUser user = appUserService.getById(order.getUserId());
            if (user != null) {
                order.setNickname(user.getNickname());
                order.setAvatarUrl(user.getAvatarUrl());
            }
            java.util.List<ExchangeLogistics> logistics = logisticsService.lambdaQuery()
                    .eq(ExchangeLogistics::getOrderId, id)
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
     * 订单列表（分页+筛选）
     */
    @GetMapping("/orders")
    public Map<String, Object> getOrders(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            LambdaQueryWrapper<ExchangeOrder> wrapper = new LambdaQueryWrapper<>();
            if (status != null) {
                wrapper.eq(ExchangeOrder::getStatus, status);
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                wrapper.and(w -> w.like(ExchangeOrder::getOrderNo, keyword.trim())
                        .or().like(ExchangeOrder::getReceiverName, keyword.trim())
                        .or().like(ExchangeOrder::getReceiverPhone, keyword.trim())
                        .or().like(ExchangeOrder::getProductName, keyword.trim()));
            }
            wrapper.orderByDesc(ExchangeOrder::getCreateTime);

            Page<ExchangeOrder> pageResult = orderService.page(new Page<>(page, size), wrapper);

            Set<Long> userIds = pageResult.getRecords().stream()
                    .map(ExchangeOrder::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, AppUser> users = userIds.isEmpty()
                    ? Map.of()
                    : appUserService.listByIds(userIds).stream()
                            .collect(Collectors.toMap(AppUser::getId, Function.identity(), (first, ignored) -> first));

            for (ExchangeOrder order : pageResult.getRecords()) {
                AppUser user = users.get(order.getUserId());
                if (user != null) {
                    order.setNickname(user.getNickname());
                    order.setAvatarUrl(user.getAvatarUrl());
                }
            }

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
     * 发货
     */
    @PostMapping("/order/{id}/ship")
    public Map<String, Object> shipOrder(@PathVariable Long id, @RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            ExchangeOrder order = orderService.getById(id);
            if (order == null) {
                result.put("code", 404);
                result.put("msg", "订单不存在");
                return result;
            }

            if (order.getStatus() != ExchangeOrder.STATUS_PENDING) {
                result.put("code", 400);
                result.put("msg", "只有待发货订单才能发货");
                return result;
            }

            String expressCompany = trimToNull(params.get("expressCompany"));
            String expressNo = trimToNull(params.get("expressNo"));
            String adminRemark = trimToNull(params.get("adminRemark"));
            if (expressCompany == null || expressNo == null) {
                result.put("code", 400);
                result.put("msg", "快递公司和快递单号不能为空");
                return result;
            }
            if (expressCompany.length() > 50 || expressNo.length() > 100) {
                result.put("code", 400);
                result.put("msg", "快递公司或快递单号长度超出限制");
                return result;
            }

            boolean updated = orderService.lambdaUpdate()
                    .set(ExchangeOrder::getStatus, ExchangeOrder.STATUS_SHIPPED)
                    .set(ExchangeOrder::getExpressCompany, expressCompany)
                    .set(ExchangeOrder::getExpressNo, expressNo)
                    .set(ExchangeOrder::getShipTime, LocalDateTime.now())
                    .set(ExchangeOrder::getUpdateTime, LocalDateTime.now())
                    .set(adminRemark != null, ExchangeOrder::getAdminRemark, adminRemark)
                    .eq(ExchangeOrder::getId, id)
                    .eq(ExchangeOrder::getStatus, ExchangeOrder.STATUS_PENDING)
                    .update();
            if (!updated) {
                result.put("code", 400);
                result.put("msg", "订单状态已变化，请刷新后重试");
                return result;
            }

            // 写入物流记录
            ExchangeLogistics log = new ExchangeLogistics();
            log.setOrderId(id);
            log.setStatus(ExchangeOrder.STATUS_SHIPPED);
            log.setDescription("已发货，快递公司：" + expressCompany + "，单号：" + expressNo);
            log.setOperator("管理员");
            log.setCreateTime(LocalDateTime.now());
            logisticsService.save(log);

            result.put("code", 200);
            result.put("msg", "发货成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "发货操作失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 管理员退回订单：取消订单并返还用户余额、库存，扣回邀请人分润。
     */
    @PostMapping("/order/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String adminRemark = params != null && params.get("adminRemark") != null
                    ? params.get("adminRemark").toString()
                    : null;
            orderService.cancelOrderByAdmin(id, adminRemark);
            result.put("code", 200);
            result.put("msg", "订单已退回，积分已返还");
        } catch (RuntimeException e) {
            result.put("code", 400);
            result.put("msg", e.getMessage());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "退回失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 更新物流状态
     */
    @PostMapping("/order/{id}/updateStatus")
    public Map<String, Object> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            ExchangeOrder order = orderService.getById(id);
            if (order == null) {
                result.put("code", 404);
                result.put("msg", "订单不存在");
                return result;
            }

            Integer newStatus = Integer.valueOf(params.get("status").toString());
            String description = params.containsKey("description") ? params.get("description").toString() : null;

            boolean validTransition = (order.getStatus() == ExchangeOrder.STATUS_SHIPPED
                    && (newStatus == ExchangeOrder.STATUS_IN_TRANSIT
                            || newStatus == ExchangeOrder.STATUS_RECEIVED))
                    || (order.getStatus() == ExchangeOrder.STATUS_IN_TRANSIT
                            && newStatus == ExchangeOrder.STATUS_RECEIVED);
            if (!validTransition) {
                result.put("code", 400);
                result.put("msg", "当前订单状态不支持该操作");
                return result;
            }

            boolean updated = orderService.lambdaUpdate()
                    .set(ExchangeOrder::getStatus, newStatus)
                    .set(newStatus == ExchangeOrder.STATUS_RECEIVED, ExchangeOrder::getReceiveTime, LocalDateTime.now())
                    .set(ExchangeOrder::getUpdateTime, LocalDateTime.now())
                    .eq(ExchangeOrder::getId, id)
                    .eq(ExchangeOrder::getStatus, order.getStatus())
                    .update();
            if (!updated) {
                result.put("code", 400);
                result.put("msg", "订单状态已变化，请刷新后重试");
                return result;
            }

            // 写入物流记录
            ExchangeLogistics log = new ExchangeLogistics();
            log.setOrderId(id);
            log.setStatus(newStatus);
            log.setDescription(description != null ? description : getStatusDescription(newStatus));
            log.setOperator("管理员");
            log.setCreateTime(LocalDateTime.now());
            logisticsService.save(log);

            result.put("code", 200);
            result.put("msg", "状态更新成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "更新失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 兑换统计
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("code", 200);
            result.put("data", orderService.getStatistics());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取统计失败: " + e.getMessage());
        }
        return result;
    }

    private String getStatusDescription(int status) {
        switch (status) {
            case 0: return "等待发货";
            case 1: return "已发货";
            case 2: return "运输中";
            case 3: return "已到货";
            case 4: return "订单已取消，余额已退还";
            default: return "状态更新";
        }
    }

    private String validateProduct(ExchangeProduct product) {
        if (product == null) {
            return "商品信息不能为空";
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            return "设备名称不能为空";
        }
        product.setName(product.getName().trim());
        if (product.getName().length() > 100) {
            return "设备名称不能超过100个字符";
        }
        if (!isValidPrice(product.getBasePrice())) {
            return "普通用户价格必须大于0且不能超过两位小数";
        }
        boolean hasInvalidLevelPrice = java.util.stream.Stream.of(
                product.getPriceLevel1(),
                product.getPriceLevel2(),
                product.getPriceLevel3(),
                product.getPriceLevel4(),
                product.getPriceLevel5())
                .anyMatch(price -> price != null && !isValidPrice(price));
        if (hasInvalidLevelPrice) {
            return "等级价格必须大于0且不能超过两位小数";
        }
        if (product.getStock() == null) {
            product.setStock(0);
        }
        if (product.getStock() < 0) {
            return "库存不能小于0";
        }
        if (product.getSortOrder() == null) {
            product.setSortOrder(0);
        }
        if (product.getSortOrder() < 0) {
            return "排序不能小于0";
        }
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        if (product.getStatus() != 0 && product.getStatus() != 1) {
            return "商品状态不正确";
        }
        if (product.getDescription() != null) {
            product.setDescription(product.getDescription().trim());
        }
        if (product.getImageUrl() != null) {
            product.setImageUrl(product.getImageUrl().trim());
            if (product.getImageUrl().length() > 500) {
                return "商品图片地址不能超过500个字符";
            }
        }
        return null;
    }

    private boolean isValidPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return price.stripTrailingZeros().scale() <= 2
                && price.compareTo(new BigDecimal("99999999999999.99")) <= 0;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
