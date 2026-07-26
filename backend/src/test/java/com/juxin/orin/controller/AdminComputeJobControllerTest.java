package com.juxin.orin.controller;

import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.service.IComputeJobService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminComputeJobControllerTest {

    @Test
    void dispatchCreatesServerOwnedJobIdentity() {
        IComputeJobService service = mock(IComputeJobService.class);
        when(service.save(any(ComputeJob.class))).thenReturn(true);
        AdminComputeJobController controller = new AdminComputeJobController();
        ReflectionTestUtils.setField(controller, "computeJobService", service);

        ComputeJob job = new ComputeJob();
        job.setTaskType("ollama");
        job.setTaskId("client-controlled");

        var result = controller.dispatchTask(job);

        assertEquals(200, result.getCode());
        assertNotNull(job.getTaskId());
        org.junit.jupiter.api.Assertions.assertNotEquals("client-controlled", job.getTaskId());
        assertEquals("pending", job.getStatus());
        assertEquals(0, job.getRetryCount());
        assertEquals(0, job.getDeleted());
        verify(service).save(job);
    }
}
