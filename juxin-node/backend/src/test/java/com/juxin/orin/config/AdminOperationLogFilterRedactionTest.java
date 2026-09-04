package com.juxin.orin.config;

import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void multipartRequestsBypassTheContentCachingWrapper() {
        MockHttpServletRequest multipart = new MockHttpServletRequest();
        multipart.setContentType("multipart/form-data; boundary=upload-boundary");
        MockHttpServletRequest json = new MockHttpServletRequest();
        json.setContentType("application/json;charset=UTF-8");

        assertFalse(AdminOperationLogFilter.shouldCacheRequestBody(multipart));
        assertTrue(AdminOperationLogFilter.shouldCacheRequestBody(json));
    }

    @Test
    void authenticatedMultipartRequestReachesTheChainUnwrapped() throws Exception {
        String token = JwtUtil.generateToken(1L, "admin", "admin", "admin");
        AdminAuthValidator validator = new AdminAuthValidator(null) {
            @Override
            public String normalizeToken(String authorization) {
                return token;
            }

            @Override
            public String validate(String authorization) {
                return null;
            }
        };

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload/image");
        request.addHeader("Authorization", "Bearer " + token);
        request.setContentType("multipart/form-data; boundary=upload-boundary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<jakarta.servlet.ServletRequest> chainedRequest = new AtomicReference<>();

        new AdminOperationLogFilter(validator).doFilter(
                request,
                response,
                (currentRequest, currentResponse) -> chainedRequest.set(currentRequest));

        assertSame(request, chainedRequest.get());
    }
}
