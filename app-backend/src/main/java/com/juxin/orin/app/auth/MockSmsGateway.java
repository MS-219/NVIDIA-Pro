package com.juxin.orin.app.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development gateway. Production must use provider=aliyun. */
public final class MockSmsGateway implements SmsGateway {
    private static final Logger log = LoggerFactory.getLogger(MockSmsGateway.class);
    private volatile String lastPhone;
    private volatile String lastCode;

    @Override
    public SendResult sendLoginCode(String phone, String code, String requestId) {
        lastPhone = phone;
        lastCode = code;
        log.info("[mock-sms] requestId={}, phone={}, code={}", requestId, phone, code);
        return new SendResult("mock-" + requestId);
    }

    String lastPhone() {
        return lastPhone;
    }

    String lastCode() {
        return lastCode;
    }
}
