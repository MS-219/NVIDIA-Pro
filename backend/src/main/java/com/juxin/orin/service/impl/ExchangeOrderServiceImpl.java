package com.juxin.orin.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.*;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.mapper.ExchangeOrderMapper;
import com.juxin.orin.mapper.ExchangeProductMapper;
import com.juxin.orin.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import com.juxin.orin.util.LogisticsUtil;

@Service
public class ExchangeOrderServiceImpl extends ServiceImpl<ExchangeOrderMapper, ExchangeOrder> implements IExchangeOrderService {

    @Autowired
    private IAppUserService appUserService;

    @Autowired
    private IExchangeProductService productService;

    @Autowired
    private IUserAddressService addressService;

    @Autowired
    private IExchangeLogisticsService logisticsService;

    @Autowired
    private ISystemConfigService configService;

    @Autowired
    private LogisticsUtil logisticsUtil;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private ExchangeProductMapper exchangeProductMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(Long userId, Long productId, Long addressId, Integer quantity, String remark) {
        if (quantity == null || quantity < 1) {
            quantity = 1;
        }

        // 1. 查询用户
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        int userLevel = user.getLevel() != null ? user.getLevel() : 0;
        if (userLevel < 1) {
            throw new RuntimeException("当前等级不支持兑换，请先升级");
        }

        // 2. 查询商品
        ExchangeProduct product = productService.getById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new RuntimeException("商品不存在或已下架");
        }
        if (product.getStock() != null && product.getStock() < quantity) {
            throw new RuntimeException("库存不足");
        }

        // 3. 查询收货地址
        UserAddress address = addressService.getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new RuntimeException("收货地址无效");
        }

        // 4. 计算价格
        BigDecimal unitPrice = product.getPriceByLevel(userLevel);
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // 5. 读取算力兑换比例（从系统配置动态读取）
        String rateStr = configService.getConfig("earnings.hashratePerYuan", "200");
        long hashrateRate = Long.parseLong(rateStr);
        long hashrateCost = totalPrice.longValue() * hashrateRate;

        // 6. 校验余额
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        if (balance.compareTo(totalPrice) < 0) {
            throw new RuntimeException("算力值不足，需要 " + hashrateCost + " 算力值，当前可用 " + (balance.longValue() * hashrateRate));
        }

        // 7. 扣减余额
        appUserService.lambdaUpdate()
                .set(AppUser::getBalance, balance.subtract(totalPrice))
                .eq(AppUser::getId, userId)
                .update();

        // 8. 扣减库存
        if (product.getStock() != null) {
            productService.lambdaUpdate()
                    .set(ExchangeProduct::getStock, product.getStock() - quantity)
                    .eq(ExchangeProduct::getId, productId)
                    .update();
        }

        // 9. 生成订单号
        String orderNo = generateOrderNo();

        // 10. 创建订单
        ExchangeOrder order = new ExchangeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductName(product.getName());
        order.setProductImage(product.getImageUrl());
        order.setQuantity(quantity);
        order.setUnitPrice(unitPrice);
        order.setTotalPrice(totalPrice);
        order.setHashrateCost(hashrateCost);
        order.setUserLevel(userLevel);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(address.getFullAddress());
        order.setStatus(ExchangeOrder.STATUS_PENDING);
        order.setRemark(remark);
        order.setCreateTime(LocalDateTime.now());

        // 11. 邀请人差价分润
        BigDecimal inviterProfit = BigDecimal.ZERO;
        if (user.getInviterId() != null) {
            AppUser inviter = appUserService.getById(user.getInviterId());
            if (inviter != null) {
                int inviterLevel = inviter.getLevel() != null ? inviter.getLevel() : 0;
                order.setInviterId(inviter.getId());
                order.setInviterLevel(inviterLevel);

                // 只有邀请人等级 > 用户等级时才有差价
                if (inviterLevel > userLevel) {
                    BigDecimal inviterPrice = product.getPriceByLevel(inviterLevel);
                    // 每件差价 = 用户单价 - 邀请人单价
                    BigDecimal priceDiff = unitPrice.subtract(inviterPrice);
                    if (priceDiff.compareTo(BigDecimal.ZERO) > 0) {
                        inviterProfit = priceDiff.multiply(BigDecimal.valueOf(quantity));
                        // 打入邀请人余额
                        BigDecimal inviterBalance = inviter.getBalance() != null ? inviter.getBalance() : BigDecimal.ZERO;
                        appUserService.lambdaUpdate()
                                .set(AppUser::getBalance, inviterBalance.add(inviterProfit))
                                .eq(AppUser::getId, inviter.getId())
                                .update();
                    }
                }
                // 平级或邀请人等级更低，差价为0，不分润
            }
        }
        order.setInviterProfit(inviterProfit);
        this.save(order);

        // 12. 写入初始物流记录
        ExchangeLogistics log = new ExchangeLogistics();
        log.setOrderId(order.getId());
        log.setStatus(ExchangeOrder.STATUS_PENDING);
        log.setDescription("订单已创建，等待发货");
        log.setOperator("系统");
        log.setCreateTime(LocalDateTime.now());
        logisticsService.save(log);

        return orderNo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByAdmin(Long orderId, String adminRemark) {
        ExchangeOrder order = this.getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (order.getStatus() == ExchangeOrder.STATUS_CANCELLED) {
            throw new RuntimeException("订单已退回，请勿重复操作");
        }
        if (order.getStatus() == ExchangeOrder.STATUS_RECEIVED) {
            throw new RuntimeException("已到货订单不支持退回");
        }

        BigDecimal refundAmount = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            int affected = appUserMapper.addBalance(order.getUserId(), refundAmount);
            if (affected != 1) {
                throw new RuntimeException("用户余额返还失败");
            }
        }

        Integer quantity = order.getQuantity() != null ? order.getQuantity() : 0;
        if (order.getProductId() != null && quantity > 0) {
            exchangeProductMapper.addStock(order.getProductId(), quantity);
        }

        BigDecimal inviterProfit = order.getInviterProfit() != null ? order.getInviterProfit() : BigDecimal.ZERO;
        if (order.getInviterId() != null && inviterProfit.compareTo(BigDecimal.ZERO) > 0) {
            int affected = appUserMapper.addBalance(order.getInviterId(), inviterProfit.negate());
            if (affected != 1) {
                throw new RuntimeException("邀请人分润扣回失败");
            }
        }

        String finalRemark = adminRemark != null && !adminRemark.trim().isEmpty()
                ? adminRemark.trim()
                : "管理员退回订单，余额已返还";

        boolean updated = this.lambdaUpdate()
                .set(ExchangeOrder::getStatus, ExchangeOrder.STATUS_CANCELLED)
                .set(ExchangeOrder::getAdminRemark, finalRemark)
                .set(ExchangeOrder::getUpdateTime, LocalDateTime.now())
                .eq(ExchangeOrder::getId, orderId)
                .ne(ExchangeOrder::getStatus, ExchangeOrder.STATUS_CANCELLED)
                .ne(ExchangeOrder::getStatus, ExchangeOrder.STATUS_RECEIVED)
                .update();
        if (!updated) {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }

        ExchangeLogistics log = new ExchangeLogistics();
        log.setOrderId(orderId);
        log.setStatus(ExchangeOrder.STATUS_CANCELLED);
        log.setDescription(finalRemark);
        log.setOperator("管理员");
        log.setCreateTime(LocalDateTime.now());
        logisticsService.save(log);
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", this.count());
        stats.put("pendingOrders", this.lambdaQuery().eq(ExchangeOrder::getStatus, ExchangeOrder.STATUS_PENDING).count());
        stats.put("shippedOrders", this.lambdaQuery().eq(ExchangeOrder::getStatus, ExchangeOrder.STATUS_SHIPPED).count());
        stats.put("completedOrders", this.lambdaQuery().eq(ExchangeOrder::getStatus, ExchangeOrder.STATUS_RECEIVED).count());
        stats.put("cancelledOrders", this.lambdaQuery().eq(ExchangeOrder::getStatus, ExchangeOrder.STATUS_CANCELLED).count());
        return stats;
    }

    /**
     * 生成订单号: EX + 年月日时分秒 + 4位随机数
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "EX" + timestamp + random;
    }

    @Override
    public Map<String, Object> getLogisticsInfo(Long userId, Long orderId) {
        ExchangeOrder order = this.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new RuntimeException("订单不存在或无权访问");
        }
        
        if (order.getStatus() < 1 || order.getExpressNo() == null || order.getExpressNo().trim().isEmpty()) {
            throw new RuntimeException("该订单尚未发货或无单号");
        }
        
        Map<String, Object> logistics = logisticsUtil.queryLogistics(order.getExpressNo());
        if (logistics == null) {
            throw new RuntimeException("查询物流信息失败，请稍后重试");
        }
        
        // 也可以包装一下把运单号放进去，不过接口本身返回 result.number
        return logistics;
    }
}
