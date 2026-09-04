package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.ApiMerchant;
import com.juxin.orin.mapper.ApiMerchantMapper;
import com.juxin.orin.service.IApiMerchantService;
import org.springframework.stereotype.Service;

/**
 * 接口商户 Service 实现类
 */
@Service
public class AiMerchantServiceImpl extends ServiceImpl<ApiMerchantMapper, ApiMerchant> implements IApiMerchantService {

    @Override
    public ApiMerchant getByAppId(String appId) {
        return getOne(new LambdaQueryWrapper<ApiMerchant>()
                .eq(ApiMerchant::getAppId, appId)
                .eq(ApiMerchant::getStatus, 1));
    }
}
