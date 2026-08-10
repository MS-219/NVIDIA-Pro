package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.SysUser;
import com.juxin.orin.service.ISysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysUserControllerLoginTest {

    @Test
    void releasesTokenOnlyAfterThreeValidPasswordAttempts() {
        String username = "admin-" + UUID.randomUUID();
        String password = "correct-password";
        ISysUserService userService = mock(ISysUserService.class);
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername(username);
        user.setRole("admin");

        when(userService.login(username, password)).thenReturn("test-token");
        when(userService.getOne(any())).thenReturn(user);

        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "sysUserService", userService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        Map<String, String> credentials = Map.of("username", username, "password", password);

        Result<Object> first = controller.login(credentials, request);
        Result<Object> second = controller.login(credentials, request);
        Result<Object> third = controller.login(credentials, request);
        Result<Object> afterSuccess = controller.login(credentials, request);

        assertEquals(500, first.getCode());
        assertEquals("用户名或密码错误", first.getMsg());
        assertEquals(500, second.getCode());
        assertEquals("用户名或密码错误", second.getMsg());
        assertEquals(200, third.getCode());
        assertNotNull(third.getData());
        assertEquals("test-token", ((Map<?, ?>) third.getData()).get("token"));
        assertEquals(500, afterSuccess.getCode());
        assertEquals("用户名或密码错误", afterSuccess.getMsg());
    }

    @Test
    void invalidPasswordDoesNotAdvanceValidAttemptCount() {
        String username = "admin-" + UUID.randomUUID();
        String password = "correct-password";
        String wrongPassword = "wrong-password";
        ISysUserService userService = mock(ISysUserService.class);
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername(username);
        user.setRole("admin");

        when(userService.login(username, password)).thenReturn("test-token");
        when(userService.login(username, wrongPassword)).thenReturn(null);
        when(userService.getOne(any())).thenReturn(user);

        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "sysUserService", userService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.11");
        Map<String, String> validCredentials = Map.of("username", username, "password", password);
        Map<String, String> invalidCredentials = Map.of("username", username, "password", wrongPassword);

        Result<Object> firstValid = controller.login(validCredentials, request);
        Result<Object> invalid = controller.login(invalidCredentials, request);
        Result<Object> secondValid = controller.login(validCredentials, request);
        Result<Object> thirdValid = controller.login(validCredentials, request);

        assertEquals(500, firstValid.getCode());
        assertEquals(500, invalid.getCode());
        assertEquals(500, secondValid.getCode());
        assertEquals(200, thirdValid.getCode());
    }
}
