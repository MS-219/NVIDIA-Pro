package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.AiDeviceTask;

import java.util.Map;

public interface IAiDeviceTaskService extends IService<AiDeviceTask> {

    /**
     * Admin: Get paginated device task list
     */
    Page<AiDeviceTask> getAdminTaskList(Integer page, Integer size, String deviceSn, String status);

    /**
     * Admin: Get statistics about device tasks
     */
    Map<String, Object> getStatistics();

    /**
     * Admin: Get task execution trend for last 24 hours
     */
    Map<String, Object> getTaskTrend();

    /**
     * Admin: Get N latest task logs
     */
    java.util.List<AiDeviceTask> getLatestTasks(Integer limit);

}
