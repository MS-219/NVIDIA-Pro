package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.ExchangeProduct;
import com.juxin.orin.mapper.ExchangeProductMapper;
import com.juxin.orin.service.IExchangeProductService;
import org.springframework.stereotype.Service;

@Service
public class ExchangeProductServiceImpl extends ServiceImpl<ExchangeProductMapper, ExchangeProduct> implements IExchangeProductService {
}
