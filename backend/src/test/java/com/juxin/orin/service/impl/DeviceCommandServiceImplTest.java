package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.mapper.DeviceCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCommandServiceImplTest {

    @Mock
    private DeviceCommandMapper commandMapper;

    private TestableDeviceCommandService service;

    @BeforeEach
    void setUp() {
        service = new TestableDeviceCommandService();
        service.setMapper(commandMapper);
    }

    @Test
    void takeRedeliversUnacknowledgedCommandAfterResponseLoss() {
        DeviceCommand delivered = command(21L, "delivered");
        when(commandMapper.selectOne(any(QueryWrapper.class))).thenReturn(delivered);

        assertEquals(delivered, service.takePendingCommand("ORIN-001"));

        verify(commandMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void takeClaimsPendingCommandConditionally() {
        DeviceCommand pending = command(22L, "pending");
        when(commandMapper.selectOne(any(QueryWrapper.class))).thenReturn(null, pending);
        when(commandMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        DeviceCommand delivered = service.takePendingCommand("ORIN-001");

        assertEquals("delivered", delivered.getStatus());
        verify(commandMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void duplicateCommandResultIsIdempotent() {
        DeviceCommand completed = command(23L, "completed");
        when(commandMapper.selectOne(any(QueryWrapper.class))).thenReturn(completed);

        assertTrue(service.submitResult("CMD-23", 0, "ok"));

        verify(commandMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    private DeviceCommand command(long id, String status) {
        DeviceCommand command = new DeviceCommand();
        command.setId(id);
        command.setCommandNo("CMD-" + id);
        command.setDeviceSn("ORIN-001");
        command.setCommandType("HEALTH_CHECK");
        command.setStatus(status);
        return command;
    }

    private static class TestableDeviceCommandService extends DeviceCommandServiceImpl {
        void setMapper(DeviceCommandMapper mapper) {
            this.baseMapper = mapper;
        }
    }
}
