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


import java.time.LocalDateTime;
import java.util.HashMap;

import java.util.Map;

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
            if (product.getId() != null) {
                product.setUpdateTime(LocalDateTime.now());
                productService.updateById(product);
            } else {
                product.setCreateTime(LocalDateTime.now());
                product.setUpdateTime(LocalDateTime.now());
                if (product.getStatus() == null) product.setStatus(1);
                if (product.getStock() == null) product.setStock(0);
                if (product.getSortOrder() == null) product.setSortOrder(0);
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

            // 关联用户昵称
            for (ExchangeOrder order : pageResult.getRecords()) {
                AppUser user = appUserService.getById(order.getUserId());
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

            String expressCompany = params.get("expressCompany");
            String expressNo = params.get("expressNo");
            String adminRemark = params.get("adminRemark");

            orderService.lambdaUpdate()
                    .set(ExchangeOrder::getStatus, ExchangeOrder.STATUS_SHIPPED)
                    .set(ExchangeOrder::getExpressCompany, expressCompany)
                    .set(ExchangeOrder::getExpressNo, expressNo)
                    .set(ExchangeOrder::getShipTime, LocalDateTime.now())
                    .set(adminRemark != null, ExchangeOrder::getAdminRemark, adminRemark)
                    .eq(ExchangeOrder::getId, id)
                    .update();

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

            // 兑换订单不允许取消
            if (newStatus == ExchangeOrder.STATUS_CANCELLED) {
                result.put("code", 400);
                result.put("msg", "请使用退回功能取消订单");
                return result;
            }

            orderService.lambdaUpdate()
                    .set(ExchangeOrder::getStatus, newStatus)
                    .set(newStatus == ExchangeOrder.STATUS_RECEIVED, ExchangeOrder::getReceiveTime, LocalDateTime.now())
                    .eq(ExchangeOrder::getId, id)
                    .update();

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
}
