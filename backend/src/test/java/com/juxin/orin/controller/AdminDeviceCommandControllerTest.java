package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.dto.DeviceCommandGroup;
import com.juxin.orin.mapper.DeviceCommandMapper;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IDeviceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDeviceCommandControllerTest {

    @Mock
    private IDeviceCommandService deviceCommandService;

    @Mock
    private IDeviceService deviceService;

    @Mock
    private DeviceCommandMapper deviceCommandMapper;

    @InjectMocks
    private AdminDeviceCommandController controller;

    @Test
    void groupsUsesExplicitCountAndDisablesAutomaticCountSql() {
        Page<DeviceCommandGroup> recordsPage = new Page<>(2, 20, false);
        when(deviceCommandMapper.selectGroupPage(any(Page.class), eq("SN001"), eq("CUSTOM"), eq("failed")))
                .thenReturn(recordsPage);
        when(deviceCommandMapper.selectGroupCount("SN001", "CUSTOM", "failed")).thenReturn(37L);

        Result<IPage<DeviceCommandGroup>> response = controller.groups(
                2,
                20,
                " SN001 ",
                " CUSTOM ",
                " failed ");

        assertEquals(200, response.getCode());
        assertEquals(37L, response.getData().getTotal());

        ArgumentCaptor<Page<?>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(deviceCommandMapper).selectGroupPage(
                pageCaptor.capture(),
                eq("SN001"),
                eq("CUSTOM"),
                eq("failed"));
        assertFalse(pageCaptor.getValue().searchCount());
    }
}
