package com.juxin.orin.app.auth;

import com.aliyun.dysmsapi20170525.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsGatewayConfig {

    @Bean
    SmsGateway smsGateway(AppProperties properties, ObjectMapper objectMapper) {
        if (!"aliyun".equalsIgnoreCase(properties.getSms().getProvider())) {
            return new MockSmsGateway();
        }

        AppProperties.Aliyun aliyun = properties.getSms().getAliyun();
        if (isBlank(aliyun.getAccessKeyId()) || isBlank(aliyun.getAccessKeySecret())) {
            throw new ApiException(500, "阿里云短信凭据未配置");
        }
        try {
            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                    .setAccessKeyId(aliyun.getAccessKeyId())
                    .setAccessKeySecret(aliyun.getAccessKeySecret())
                    .setEndpoint(aliyun.getEndpoint())
                    // Do not let a stalled provider call hold an application
                    // request indefinitely. Values remain configurable but
                    // are bounded to operationally sensible limits.
                    .setConnectTimeout(timeoutMillis(aliyun.getConnectTimeoutMillis(), 5_000))
                    .setReadTimeout(timeoutMillis(aliyun.getReadTimeoutMillis(), 10_000));
            Client client = new Client(config);
            return new AliyunSmsGateway(client, aliyun, objectMapper);
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化阿里云短信客户端", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int timeoutMillis(int configured, int fallback) {
        if (configured <= 0) {
            return fallback;
        }
        return Math.min(configured, 60_000);
    }
}
