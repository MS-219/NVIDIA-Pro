package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IComputeJobService;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IDeviceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrinDeviceControllerTest {

    @Mock
    private IDeviceService deviceService;

    @Mock
    private IComputeJobService computeJobService;

    @Mock
    private IDeviceCommandService deviceCommandService;

    @Mock
    private IAppUserService appUserService;

    @InjectMocks
    private AdminOrinDeviceController controller;

    @Test
    void listFillsBoundUserProfileWithSingleBatchQuery() {
        Device firstBoundDevice = device(1L, 101L);
        Device secondBoundDevice = device(2L, 202L);
        Device unboundDevice = device(3L, null);
        Page<Device> devicePage = new Page<>(1, 10);
        devicePage.setRecords(List.of(firstBoundDevice, secondBoundDevice, unboundDevice));

        AppUser firstUser = user(101L, "用户甲", "https://cdn.example.com/avatar-101.png");
        AppUser secondUser = user(202L, "用户乙", "https://cdn.example.com/avatar-202.png");
        when(deviceService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(devicePage);
        when(appUserService.listByIds(any(Collection.class))).thenReturn(List.of(firstUser, secondUser));

        var response = controller.list(1, 10, null, null, null, null, 2);

        assertEquals(200, response.getCode());
        IPage<Device> result = response.getData();
        assertEquals(101L, result.getRecords().get(0).getUserId());
        assertEquals("用户甲", result.getRecords().get(0).getNickname());
        assertEquals("https://cdn.example.com/avatar-101.png", result.getRecords().get(0).getAvatarUrl());
        assertEquals(202L, result.getRecords().get(1).getUserId());
        assertEquals("用户乙", result.getRecords().get(1).getNickname());
        assertEquals("https://cdn.example.com/avatar-202.png", result.getRecords().get(1).getAvatarUrl());
        assertNull(result.getRecords().get(2).getUserId());
        assertNull(result.getRecords().get(2).getNickname());
        assertNull(result.getRecords().get(2).getAvatarUrl());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> userIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(appUserService).listByIds(userIdsCaptor.capture());
        assertEquals(Set.of(101L, 202L), Set.copyOf(userIdsCaptor.getValue()));
    }

    private Device device(Long id, Long userId) {
        Device device = new Device();
        device.setId(id);
        device.setUserId(userId);
        device.setSn("");
        return device;
    }

    private AppUser user(Long id, String nickname, String avatarUrl) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        return user;
    }
}
