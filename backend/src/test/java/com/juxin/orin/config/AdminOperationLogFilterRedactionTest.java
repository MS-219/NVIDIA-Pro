package com.juxin.orin.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOperationLogFilterRedactionTest {

    @Test
    void redactsCredentialsIdentityAndFinancialFields() {
        String body = """
                {"phone":"13800138000","idCard":"110101199001011234","bankCardNo":"62220000",
                 "balance":123.45,"amount":"9.90","password":"secret","remark":"keep"}
                """;

        String redacted = AdminOperationLogFilter.redactSensitiveBody(body);

        assertFalse(redacted.contains("13800138000"));
        assertFalse(redacted.contains("110101199001011234"));
        assertFalse(redacted.contains("62220000"));
        assertFalse(redacted.contains("123.45"));
        assertFalse(redacted.contains("9.90"));
        assertFalse(redacted.contains("secret"));
        assertTrue(redacted.contains("\"remark\":\"keep\""));
    }

    @Test
    void redactsEveryQueryParameterValue() {
        String redacted = AdminOperationLogFilter.redactQueryString(
                "keyword=13800138000&openid=oSensitive&page=1&status=0");

        assertFalse(redacted.contains("13800138000"));
        assertFalse(redacted.contains("oSensitive"));
        assertFalse(redacted.contains("page=1"));
        assertTrue(redacted.contains("keyword=***"));
        assertTrue(redacted.contains("openid=***"));
        assertTrue(redacted.contains("page=***"));
    }
}
