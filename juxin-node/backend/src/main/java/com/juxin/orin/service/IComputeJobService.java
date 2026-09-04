package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.ComputeJob;

import java.util.Map;

public interface IComputeJobService extends IService<ComputeJob> {

    /**
     * Admin: Get paginated device task list
     */
    Page<ComputeJob> getAdminTaskList(Integer page, Integer size, String deviceSn, String status);

    /**
     * Admin: Get statistics about device tasks
     */
    Map<String, Object> getStatistics();

    /**
     * Admin: Get task execution trend for last 24 hours
     */
    Map<String, Object> getTaskTrend();

    /**
     * Atomically claim the oldest pending task that is either targeted at the
     * device or belongs to the public queue.
     */
    ComputeJob claimNextPendingTask(String deviceSn);

    /**
     * Admin: Get N latest task logs
     */
    java.util.List<ComputeJob> getLatestTasks(Integer limit);

}
