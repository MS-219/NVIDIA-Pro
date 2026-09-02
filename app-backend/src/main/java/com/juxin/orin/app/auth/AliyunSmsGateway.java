package com.juxin.orin.app.auth;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.config.AppProperties;

import java.util.Map;

/** Thin adapter around Alibaba Cloud Dysmsapi. It never exposes credentials to callers. */
public final class AliyunSmsGateway implements SmsGateway {
    private final Client client;
    private final AppProperties.Aliyun properties;
    private final ObjectMapper objectMapper;

    public AliyunSmsGateway(Client client, AppProperties.Aliyun properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SendResult sendLoginCode(String phone, String code, String requestId) {
        if (isBlank(properties.getSignName()) || isBlank(properties.getTemplateCode())) {
            throw new ApiException(503, "短信服务尚未配置签名或模板");
        }
        final String templateParam;
        try {
            templateParam = objectMapper.writeValueAsString(
                    Map.of(properties.getCodeParameterName(), code));
        } catch (JsonProcessingException e) {
            throw new ApiException(500, "短信参数生成失败");
        }

        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(properties.getSignName())
                .setTemplateCode(properties.getTemplateCode())
                .setTemplateParam(templateParam)
                .setOutId(requestId);
        try {
            SendSmsResponse response = client.sendSms(request);
            if (response == null || response.getBody() == null
                    || !"OK".equalsIgnoreCase(response.getBody().getCode())) {
                String message = response != null && response.getBody() != null
                        ? response.getBody().getMessage()
                        : "阿里云未返回有效响应";
                throw new ApiException(502, "短信发送失败: " + message);
            }
            return new SendResult(response.getBody().getBizId());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "短信服务暂时不可用");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
