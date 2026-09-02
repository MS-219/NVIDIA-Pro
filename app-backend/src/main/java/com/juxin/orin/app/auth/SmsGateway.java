package com.juxin.orin.app.auth;

public interface SmsGateway {
    SendResult sendLoginCode(String phone, String code, String requestId);

    record SendResult(String providerRequestId) {
    }
}
