package com.juxin.orin.controller;

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

import java.util.Map;

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
}
