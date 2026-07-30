package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IInviteService;
import com.juxin.orin.service.IWechatService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppUserControllerWxLoginTest {

    @Test
    @SuppressWarnings("unchecked")
    void wxLoginReusesServiceUserAndPreservesResponseAndProfileUpdates() {
        IAppUserService appUserService = mock(IAppUserService.class);
        IWechatService wechatService = mock(IWechatService.class);
        IInviteService inviteService = mock(IInviteService.class);
        AppUserController controller = new AppUserController();
        ReflectionTestUtils.setField(controller, "appUserService", appUserService);
        ReflectionTestUtils.setField(controller, "wechatService", wechatService);
        ReflectionTestUtils.setField(controller, "inviteService", inviteService);

        AppUser user = new AppUser();
        user.setId(123456L);
        user.setOpenid("openid-new");
        user.setNickname("微信用户");
        user.setLevel(0);
        when(wechatService.code2Session("wx-code")).thenReturn("openid-new");
        when(appUserService.wxLoginWithUser("openid-new"))
                .thenReturn(new IAppUserService.WxLoginResult("jwt-token", user, true));

        Result<Object> result = controller.wxLogin(Map.of(
                "code", "wx-code",
                "nickname", "新昵称",
                "avatarUrl", "https://example.test/avatar.png",
                "inviteCode", "INVITE-1"));

        assertEquals(200, result.getCode());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("jwt-token", data.get("token"));
        assertEquals(123456L, data.get("userId"));
        assertEquals(true, data.get("isNewUser"));
        assertEquals("新昵称", data.get("nickname"));
        assertEquals("https://example.test/avatar.png", data.get("avatarUrl"));
        assertEquals(0, data.get("level"));
        verify(appUserService).wxLoginWithUser("openid-new");
        verify(appUserService, never()).getByOpenid(anyString());
        verify(inviteService).handleNewUserInvite(123456L, "INVITE-1");
        verify(appUserService).updateById(user);
    }
}
