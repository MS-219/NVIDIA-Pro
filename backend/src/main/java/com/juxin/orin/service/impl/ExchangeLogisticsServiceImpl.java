package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.ExchangeLogistics;
import com.juxin.orin.mapper.ExchangeLogisticsMapper;
import com.juxin.orin.service.IExchangeLogisticsService;
import org.springframework.stereotype.Service;

@Service
public class ExchangeLogisticsServiceImpl extends ServiceImpl<ExchangeLogisticsMapper, ExchangeLogistics> implements IExchangeLogisticsService {
}
