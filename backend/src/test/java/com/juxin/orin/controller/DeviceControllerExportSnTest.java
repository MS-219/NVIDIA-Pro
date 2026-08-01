package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.SysUser;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.ISysUserService;
import com.juxin.orin.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerExportSnTest {

    @Mock
    private IDeviceService deviceService;

    @Mock
    private ISysUserService sysUserService;

    @Mock
    private LambdaQueryChainWrapper<Device> deviceQuery;

    @InjectMocks
    private DeviceController controller;

    @Test
    void factoryCanExportUnboundDeviceList() {
        SysUser factory = new SysUser();
        factory.setId(9L);
        factory.setUsername("factory");
        factory.setRole("factory");
        when(sysUserService.getById(9L)).thenReturn(factory);
        when(deviceService.lambdaQuery()).thenReturn(deviceQuery);
        when(deviceQuery.isNull(any(SFunction.class))).thenReturn(deviceQuery);
        when(deviceQuery.orderByDesc(org.mockito.ArgumentMatchers.<SFunction<Device, ?>>any()))
                .thenReturn(deviceQuery);

        Device device = new Device();
        device.setSn("VD-001");
        device.setBindCode("JX123456");
        device.setStatus(1);
        device.setCreateTime(LocalDateTime.of(2026, 8, 1, 12, 0));
        when(deviceQuery.list()).thenReturn(List.of(device));

        String token = JwtUtil.generateToken(9L, "factory", "admin", "factory");
        Result<Object> result = controller.exportSn(null, "Bearer " + token);

        assertEquals(200, result.getCode());
        Map<?, ?> payload = (Map<?, ?>) result.getData();
        assertEquals(1, payload.get("total"));
        assertEquals("JX123456", ((Map<?, ?>) ((List<?>) payload.get("list")).get(0)).get("bindCode"));
    }
}
