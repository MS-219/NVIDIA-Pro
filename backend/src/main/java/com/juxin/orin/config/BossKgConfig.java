package com.juxin.orin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 佣金保平台配置
 * 配置项从 application.yml 中读取
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bosskg")
public class BossKgConfig {

    /**
     * 是否启用佣金保
     */
    private boolean enabled = false;

    /**
     * API地址
     * 测试环境:
     * http://testgateway.serviceshare.com/testapi/clientapi/clientBusiness/common
     * 生产环境(身边云): https://api.serviceshare.com/clientapi/clientBusiness/common
     */
    private String apiUrl = "https://api.serviceshare.com/clientapi/clientBusiness/common";

    /**
     * 商户号 (merId)
     */
    private String merId;

    /**
     * 服务商ID (providerId)
     */
    private String providerId;

    /**
     * 任务ID (taskId)
     */
    private String taskId;

    /**
     * DES密钥 (ApiKey/INTER_KEY)
     */
    private String desKey;

    /**
     * 商户RSA私钥 (用于签名)
     * 注意：不要包含 -----BEGIN PRIVATE KEY----- 和 -----END PRIVATE KEY-----
     */
    private String merchantPrivateKey;

    /**
     * 佣金保平台RSA公钥 (用于验签)
     * 注意：不要包含 -----BEGIN PUBLIC KEY----- 和 -----END PUBLIC KEY-----
     */
    private String platformPublicKey;

    /**
     * 签约成功回调地址
     */
    private String contractNotifyUrl;

    /**
     * 付款结果回调地址
     */
    private String paymentNotifyUrl;

    /**
     * API版本号
     */
    private String version = "V1.0";

    /**
     * 连接超时时间(毫秒)
     */
    private int connectTimeout = 10000;

    /**
     * 读取超时时间(毫秒)
     */
    private int readTimeout = 60000;

    /**
     * 是否需要上传身份证照片
     */
    private boolean requireIdCardPic = true;

    /**
     * 默认付款备注 (建议使用"服务费"可免审)
     */
    private String defaultMemo = "服务费";
}
