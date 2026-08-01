package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.ExchangeOrder;
import com.juxin.orin.entity.ExchangeProduct;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IExchangeLogisticsService;
import com.juxin.orin.service.IExchangeOrderService;
import com.juxin.orin.service.IExchangeProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminExchangeControllerTest {

    @Mock
    private IExchangeProductService productService;

    @Mock
    private IExchangeOrderService orderService;

    @Mock
    private IExchangeLogisticsService logisticsService;

    @Mock
    private IAppUserService appUserService;

    @InjectMocks
    private AdminExchangeController controller;

    @Test
    void saveProductAcceptsEmptyLevelPricesAndInitializesDefaults() {
        ExchangeProduct product = new ExchangeProduct();
        product.setName("  Orin Nano 8GB  ");
        product.setBasePrice(new BigDecimal("3999.00"));

        Map<String, Object> result = controller.saveProduct(product);

        assertEquals(200, result.get("code"));
        assertEquals("Orin Nano 8GB", product.getName());
        assertEquals(0, product.getStock());
        assertEquals(0, product.getSortOrder());
        assertEquals(1, product.getStatus());
        assertNull(product.getPriceLevel1());
        verify(productService).save(product);
    }

    @Test
    void saveProductRejectsNegativeOrOverPrecisePrices() {
        ExchangeProduct negative = validProduct();
        negative.setBasePrice(new BigDecimal("-1"));
        ExchangeProduct overPrecise = validProduct();
        overPrecise.setPriceLevel1(new BigDecimal("99.999"));

        assertEquals(400, controller.saveProduct(negative).get("code"));
        assertEquals(400, controller.saveProduct(overPrecise).get("code"));
        verify(productService, never()).save(any(ExchangeProduct.class));
    }

    @Test
    void orderListLoadsUserProfilesWithOneBatchQuery() {
        ExchangeOrder first = order(1L, 101L);
        ExchangeOrder second = order(2L, 202L);
        Page<ExchangeOrder> page = new Page<>(1, 20);
        page.setRecords(List.of(first, second));
        when(orderService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        AppUser firstUser = user(101L, "用户甲", "https://cdn.example.com/101.png");
        AppUser secondUser = user(202L, "用户乙", "https://cdn.example.com/202.png");
        when(appUserService.listByIds(any(Collection.class))).thenReturn(List.of(firstUser, secondUser));

        Map<String, Object> result = controller.getOrders(1, 20, null, null);

        assertEquals(200, result.get("code"));
        assertEquals("用户甲", first.getNickname());
        assertEquals("https://cdn.example.com/101.png", first.getAvatarUrl());
        assertEquals("用户乙", second.getNickname());
        verify(appUserService).listByIds(any(Collection.class));
        verify(appUserService, never()).getById(any(Long.class));
    }

    @Test
    void shipOrderRejectsBlankLogisticsFields() {
        ExchangeOrder order = order(1L, 101L);
        order.setStatus(ExchangeOrder.STATUS_PENDING);
        when(orderService.getById(1L)).thenReturn(order);

        Map<String, Object> result = controller.shipOrder(1L, Map.of(
                "expressCompany", " ",
                "expressNo", " "));

        assertEquals(400, result.get("code"));
        assertEquals("快递公司和快递单号不能为空", result.get("msg"));
        verify(logisticsService, never()).save(any());
    }

    private ExchangeProduct validProduct() {
        ExchangeProduct product = new ExchangeProduct();
        product.setName("Orin Nano 8GB");
        product.setBasePrice(new BigDecimal("3999.00"));
        product.setStock(10);
        product.setStatus(1);
        product.setSortOrder(0);
        return product;
    }

    private ExchangeOrder order(Long id, Long userId) {
        ExchangeOrder order = new ExchangeOrder();
        order.setId(id);
        order.setUserId(userId);
        return order;
    }

    private AppUser user(Long id, String nickname, String avatarUrl) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        return user;
    }
}
