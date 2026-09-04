package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.SystemConfig;

import java.util.Map;

public interface ISystemConfigService extends IService<SystemConfig> {

    /**
     * 获取配置值
     */
    String getConfig(String key);

    /**
     * 获取配置值，带默认值
     */
    String getConfig(String key, String defaultValue);

    /**
     * 设置配置值
     */
    void setConfig(String key, String value);

    /**
     * 批量获取配置
     */
    Map<String, String> getConfigs(String... keys);

    /**
     * 批量设置配置
     */
    void setConfigs(Map<String, String> configs);
}
