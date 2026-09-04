package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.mapper.DeviceCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void takeCommandQueriesOnlyColumnsPresentInCommandSchema() {
        DeviceCommand pending = command(24L, "pending");
        when(commandMapper.selectOne(any(QueryWrapper.class))).thenReturn(null, pending);
        when(commandMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.takePendingCommand("ORIN-001");

        ArgumentCaptor<QueryWrapper<DeviceCommand>> queries = queryCaptor();
        verify(commandMapper, times(2)).selectOne(queries.capture());
        queries.getAllValues().forEach(query -> assertDoesNotReferenceDeleted(query.getSqlSegment()));

        ArgumentCaptor<UpdateWrapper<DeviceCommand>> update = updateCaptor();
        verify(commandMapper).update(isNull(), update.capture());
        assertDoesNotReferenceDeleted(update.getValue().getSqlSegment());
    }

    @Test
    void submitResultQueriesOnlyColumnsPresentInCommandSchema() {
        DeviceCommand delivered = command(25L, "delivered");
        when(commandMapper.selectOne(any(QueryWrapper.class))).thenReturn(delivered);
        when(commandMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        assertTrue(service.submitResult("CMD-25", 0, "ok"));

        ArgumentCaptor<QueryWrapper<DeviceCommand>> query = queryCaptor();
        verify(commandMapper).selectOne(query.capture());
        assertDoesNotReferenceDeleted(query.getValue().getSqlSegment());

        ArgumentCaptor<UpdateWrapper<DeviceCommand>> update = updateCaptor();
        verify(commandMapper).update(isNull(), update.capture());
        assertDoesNotReferenceDeleted(update.getValue().getSqlSegment());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ArgumentCaptor<QueryWrapper<DeviceCommand>> queryCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(QueryWrapper.class);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ArgumentCaptor<UpdateWrapper<DeviceCommand>> updateCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
    }

    private void assertDoesNotReferenceDeleted(String sqlSegment) {
        assertFalse(sqlSegment.toLowerCase().contains("deleted"), sqlSegment);
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
