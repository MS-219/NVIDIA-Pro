package com.juxin.orin.controller;

import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.ExchangeProduct;
import com.juxin.orin.service.InviteLevelConfigService;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IExchangeLogisticsService;
import com.juxin.orin.service.IExchangeOrderService;
import com.juxin.orin.service.IExchangeProductService;
import com.juxin.orin.service.ISystemConfigService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeControllerAuthorizationTest {

    @Mock
    private IExchangeProductService productService;

    @Mock
    private IExchangeOrderService orderService;

    @Mock
    private IExchangeLogisticsService logisticsService;

    @Mock
    private IAppUserService appUserService;

    @Mock
    private ISystemConfigService configService;

    @Mock
    private InviteLevelConfigService inviteLevelConfigService;

    @InjectMocks
    private ExchangeController controller;

    @Test
    void createOrderRequiresAppLogin() {
        Map<String, Object> params = Map.of("userId", 7L, "productId", 10L, "addressId", 20L);

        Map<String, Object> result = controller.createOrder(params, null);

        assertEquals(401, result.get("code"));
        verify(orderService, never()).createOrder(7L, 10L, 20L, 1, null);
    }

    @Test
    void createOrderRejectsAUserIdDifferentFromTheToken() {
        String token = JwtUtil.generateToken(7L, "user-7", "app");
        Map<String, Object> params = Map.of("userId", 8L, "productId", 10L, "addressId", 20L);

        Map<String, Object> result = controller.createOrder(params, "Bearer " + token);

        assertEquals(403, result.get("code"));
        verify(orderService, never()).createOrder(8L, 10L, 20L, 1, null);
    }

    @Test
    void createOrderUsesTheAuthenticatedUser() {
        String token = JwtUtil.generateToken(7L, "user-7", "app");
        Map<String, Object> params = Map.of(
                "userId", 7L,
                "productId", 10L,
                "addressId", 20L,
                "quantity", 2,
                "remark", "尽快发货");
        when(orderService.createOrder(7L, 10L, 20L, 2, "尽快发货")).thenReturn("EX202608010001");

        Map<String, Object> result = controller.createOrder(params, "Bearer " + token);

        assertEquals(200, result.get("code"));
        assertEquals(Map.of("orderNo", "EX202608010001"), result.get("data"));
        verify(orderService).createOrder(7L, 10L, 20L, 2, "尽快发货");
    }

    @Test
    void productDetailOnlyReturnsTheAuthenticatedUsersPrice() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setLevel(3);
        user.setBalance(new BigDecimal("20000.00"));

        ExchangeProduct product = new ExchangeProduct();
        product.setId(10L);
        product.setName("Orin Nano 8GB");
        product.setBasePrice(new BigDecimal("12800.00"));
        product.setPriceLevel1(new BigDecimal("12800.00"));
        product.setPriceLevel2(new BigDecimal("12000.00"));
        product.setPriceLevel3(new BigDecimal("11500.00"));

        when(productService.getById(10L)).thenReturn(product);
        when(appUserService.getById(7L)).thenReturn(user);
        when(configService.getConfig("earnings.hashratePerYuan", "100")).thenReturn("200");
        when(inviteLevelConfigService.getLevelName(3)).thenReturn("高级合伙人");

        String token = JwtUtil.generateToken(7L, "user-7", "app");
        Map<String, Object> result = controller.getProductDetail(10L, 7L, "Bearer " + token);

        assertEquals(200, result.get("code"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        Map<String, Object> visibleProduct = (Map<String, Object>) data.get("product");
        Map<String, Object> currentPrice = (Map<String, Object>) data.get("currentPrice");

        assertFalse(data.containsKey("allPrices"));
        assertFalse(data.containsKey("levelNames"));
        assertEquals("高级合伙人", data.get("userLevelName"));
        assertFalse(visibleProduct.containsKey("basePrice"));
        assertFalse(visibleProduct.containsKey("priceLevel1"));
        assertFalse(visibleProduct.containsKey("priceLevel2"));
        assertFalse(visibleProduct.containsKey("priceLevel3"));
        assertEquals(new BigDecimal("11500.00"), visibleProduct.get("userPrice"));
        assertEquals(2300000L, visibleProduct.get("userHashratePrice"));
        assertEquals(3, currentPrice.get("level"));
        assertEquals("高级合伙人", currentPrice.get("levelName"));
        assertEquals(new BigDecimal("11500.00"), currentPrice.get("price"));
    }
}
