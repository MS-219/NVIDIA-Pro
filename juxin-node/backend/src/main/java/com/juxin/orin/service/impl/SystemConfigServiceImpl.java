package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.SystemConfig;
import com.juxin.orin.mapper.SystemConfigMapper;
import com.juxin.orin.service.ISystemConfigService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig>
        implements ISystemConfigService {

    @Override
    public String getConfig(String key) {
        return getConfig(key, null);
    }

    @Override
    public String getConfig(String key, String defaultValue) {
        SystemConfig config = lambdaQuery().eq(SystemConfig::getConfigKey, key).one();
        if (config != null) {
            return config.getConfigValue();
        }
        return defaultValue;
    }

    @Override
    public void setConfig(String key, String value) {
        SystemConfig config = lambdaQuery().eq(SystemConfig::getConfigKey, key).one();
        if (config != null) {
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            updateById(config);
        } else {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            save(config);
        }
    }

    @Override
    public Map<String, String> getConfigs(String... keys) {
        Map<String, String> result = new HashMap<>();
        for (String key : keys) {
            result.put(key, getConfig(key));
        }
        return result;
    }

    @Override
    public void setConfigs(Map<String, String> configs) {
        configs.forEach(this::setConfig);
    }
}
