package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.mapper.ComputeJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComputeJobServiceImplTest {

    @Mock
    private ComputeJobMapper computeJobMapper;

    private TestableComputeJobService service;

    @BeforeEach
    void setUp() {
        service = new TestableComputeJobService();
        service.setMapper(computeJobMapper);
    }

    @Test
    void claimMarksTaskRunningForDevice() {
        ComputeJob candidate = pendingTask(11L);
        when(computeJobMapper.selectOne(any(QueryWrapper.class))).thenReturn(null, candidate);
        when(computeJobMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        ComputeJob claimed = service.claimNextPendingTask(" ORIN-001 ");

        assertNotNull(claimed);
        assertEquals(11L, claimed.getId());
        assertEquals("ORIN-001", claimed.getDeviceSn());
        assertEquals("running", claimed.getStatus());
        assertNotNull(claimed.getUpdateTime());
    }

    @Test
    void claimRetriesWhenAnotherDeviceWinsConditionalUpdate() {
        ComputeJob contested = pendingTask(11L);
        ComputeJob available = pendingTask(12L);
        when(computeJobMapper.selectOne(any(QueryWrapper.class))).thenReturn(null, contested, available);
        when(computeJobMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0, 1);

        ComputeJob claimed = service.claimNextPendingTask("ORIN-002");

        assertNotNull(claimed);
        assertEquals(12L, claimed.getId());
        assertEquals("ORIN-002", claimed.getDeviceSn());
        verify(computeJobMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void claimRecoversExistingRunningTaskForSameDevice() {
        ComputeJob running = pendingTask(15L);
        running.setStatus("running");
        running.setDeviceSn("ORIN-003");
        when(computeJobMapper.selectOne(any(QueryWrapper.class))).thenReturn(running);

        ComputeJob recovered = service.claimNextPendingTask("ORIN-003");

        assertEquals(running, recovered);
        verify(computeJobMapper, times(0)).update(isNull(), any(UpdateWrapper.class));
    }

    private ComputeJob pendingTask(long id) {
        ComputeJob task = new ComputeJob();
        task.setId(id);
        task.setStatus("pending");
        return task;
    }

    private static class TestableComputeJobService extends ComputeJobServiceImpl {
        void setMapper(ComputeJobMapper mapper) {
            this.baseMapper = mapper;
        }
    }
}
