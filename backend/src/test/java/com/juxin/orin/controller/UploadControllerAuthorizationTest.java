package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadControllerAuthorizationTest {

    private final UploadController controller = new UploadController();
    private final MockMultipartFile emptyFile =
            new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

    @Test
    void rejectsAnonymousUpload() {
        Result<Map<String, String>> result = controller.uploadImage(emptyFile, null);

        assertEquals(500, result.getCode());
        assertEquals("未登录或登录已过期", result.getMsg());
    }

    @Test
    void acceptsAppAuthenticationBeforeValidatingFile() {
        String token = JwtUtil.generateToken(7L, "app-user", "app");

        Result<Map<String, String>> result = controller.uploadImage(emptyFile, "Bearer " + token);

        assertEquals(500, result.getCode());
        assertEquals("文件不能为空", result.getMsg());
    }

    @Test
    void rejectsFactoryUpload() {
        String token = JwtUtil.generateToken(9L, "factory", "admin", "factory");

        Result<Map<String, String>> result = controller.uploadImage(emptyFile, "Bearer " + token);

        assertEquals(500, result.getCode());
        assertEquals("未登录或登录已过期", result.getMsg());
    }
}
