package com.juxin.orin.service.impl;

import com.juxin.orin.entity.AppUser;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTest {

    @Mock
    private AppUserMapper appUserMapper;

    private TestableAppUserService service;

    @BeforeEach
    void setUp() {
        service = new TestableAppUserService();
        service.setMapper(appUserMapper);
    }

    @Test
    void wxLoginWithUserQueriesExistingUserOnce() {
        AppUser user = existingUser();
        when(appUserMapper.selectByOpenidIncludingDeleted(user.getOpenid())).thenReturn(user);

        IAppUserService.WxLoginResult result = service.wxLoginWithUser(user.getOpenid());

        assertSame(user, result.user());
        assertFalse(result.isNewUser());
        assertEquals(user.getId(), JwtUtil.getUserId(result.token()));
        assertEquals("app", JwtUtil.getUserType(result.token()));
        verify(appUserMapper, times(1)).selectByOpenidIncludingDeleted(user.getOpenid());
        verify(appUserMapper, never()).insert(any(AppUser.class));
    }

    @Test
    void wxLoginWithUserCreatesAndReturnsNewUserWithoutRequery() {
        when(appUserMapper.selectByOpenidIncludingDeleted("openid-new")).thenReturn(null);
        when(appUserMapper.countByIdIncludingDeleted(anyLong())).thenReturn(0);
        when(appUserMapper.insert(any(AppUser.class))).thenReturn(1);

        IAppUserService.WxLoginResult result = service.wxLoginWithUser("openid-new");

        assertTrue(result.isNewUser());
        assertEquals("openid-new", result.user().getOpenid());
        assertEquals("微信用户", result.user().getNickname());
        assertEquals(BigDecimal.ZERO, result.user().getBalance());
        assertEquals(0, result.user().getQuota());
        assertEquals(result.user().getId(), JwtUtil.getUserId(result.token()));
        verify(appUserMapper, times(1)).selectByOpenidIncludingDeleted("openid-new");
        verify(appUserMapper).insert(result.user());
    }

    @Test
    void wxLoginWithUserStillRejectsDeletedUserFromSingleLookup() {
        AppUser user = existingUser();
        user.setDeleted(1);
        when(appUserMapper.selectByOpenidIncludingDeleted(user.getOpenid())).thenReturn(user);

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.wxLoginWithUser(user.getOpenid()));

        assertEquals("账号已删除，请联系管理员从回收站恢复", error.getMessage());
        verify(appUserMapper, times(1)).selectByOpenidIncludingDeleted(user.getOpenid());
        verify(appUserMapper, never()).insert(any(AppUser.class));
    }

    private AppUser existingUser() {
        AppUser user = new AppUser();
        user.setId(123456L);
        user.setOpenid("openid-existing");
        user.setNickname("微信用户");
        user.setLevel(2);
        return user;
    }

    private static class TestableAppUserService extends AppUserServiceImpl {
        void setMapper(AppUserMapper mapper) {
            this.baseMapper = mapper;
        }
    }
}
