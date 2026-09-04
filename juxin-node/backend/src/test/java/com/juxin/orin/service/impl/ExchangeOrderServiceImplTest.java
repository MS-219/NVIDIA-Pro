package com.juxin.orin.service.impl;

import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.ExchangeOrder;
import com.juxin.orin.entity.ExchangeProduct;
import com.juxin.orin.entity.UserAddress;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.mapper.ExchangeOrderMapper;
import com.juxin.orin.mapper.ExchangeProductMapper;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IExchangeLogisticsService;
import com.juxin.orin.service.IExchangeProductService;
import com.juxin.orin.service.ISystemConfigService;
import com.juxin.orin.service.IUserAddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeOrderServiceImplTest {

    @Mock
    private IAppUserService appUserService;

    @Mock
    private IExchangeProductService productService;

    @Mock
    private IUserAddressService addressService;

    @Mock
    private IExchangeLogisticsService logisticsService;

    @Mock
    private ISystemConfigService configService;

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private ExchangeProductMapper exchangeProductMapper;

    @Mock
    private ExchangeOrderMapper exchangeOrderMapper;

    @InjectMocks
    private ExchangeOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", exchangeOrderMapper);
    }

    @Test
    void createOrderUsesConditionalDeductionsAndKeepsDecimalHashratePrecision() {
        stubOrderInputs();
        when(appUserMapper.deductBalanceIfEnough(7L, new BigDecimal("39.99"))).thenReturn(1);
        when(exchangeProductMapper.deductStockIfEnough(10L, 1)).thenReturn(1);
        when(exchangeOrderMapper.insert(any(ExchangeOrder.class))).thenAnswer(invocation -> {
            ExchangeOrder order = invocation.getArgument(0);
            order.setId(1001L);
            return 1;
        });

        String orderNo = service.createOrder(7L, 10L, 20L, 1, null);

        verify(appUserMapper).deductBalanceIfEnough(7L, new BigDecimal("39.99"));
        verify(exchangeProductMapper).deductStockIfEnough(10L, 1);
        ArgumentCaptor<ExchangeOrder> orderCaptor = ArgumentCaptor.forClass(ExchangeOrder.class);
        verify(exchangeOrderMapper).insert(orderCaptor.capture());
        assertEquals(3999L, orderCaptor.getValue().getHashrateCost());
        assertEquals(new BigDecimal("39.99"), orderCaptor.getValue().getTotalPrice());
        assertEquals(20, orderNo.length());
        verify(logisticsService).save(any());
    }

    @Test
    void createOrderStopsBeforeWritingOrderWhenStockConditionFails() {
        stubOrderInputs();
        when(appUserMapper.deductBalanceIfEnough(7L, new BigDecimal("39.99"))).thenReturn(1);
        when(exchangeProductMapper.deductStockIfEnough(10L, 1)).thenReturn(0);

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.createOrder(7L, 10L, 20L, 1, null));

        assertEquals("库存不足或商品已下架", error.getMessage());
        verify(exchangeOrderMapper, never()).insert(any());
        verify(logisticsService, never()).save(any());
    }

    private void stubOrderInputs() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setLevel(1);
        user.setBalance(new BigDecimal("100.00"));

        ExchangeProduct product = new ExchangeProduct();
        product.setId(10L);
        product.setName("Orin Nano 8GB");
        product.setImageUrl("https://cdn.example.com/orin.png");
        product.setBasePrice(new BigDecimal("49.99"));
        product.setPriceLevel1(new BigDecimal("39.99"));
        product.setStock(3);
        product.setStatus(1);

        UserAddress address = new UserAddress();
        address.setId(20L);
        address.setUserId(7L);
        address.setReceiverName("张三");
        address.setPhone("13800000000");
        address.setProvince("浙江省");
        address.setCity("杭州市");
        address.setDetailAddress("文一路1号");

        when(appUserService.getById(7L)).thenReturn(user);
        when(productService.getById(10L)).thenReturn(product);
        when(addressService.getById(20L)).thenReturn(address);
        when(configService.getConfig("earnings.hashratePerYuan", "100")).thenReturn("100");
    }
}
