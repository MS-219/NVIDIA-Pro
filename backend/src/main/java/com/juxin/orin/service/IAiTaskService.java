package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.AiTask;

import java.util.Map;

/**
 * AI 任务服务接口
 */
public interface IAiTaskService extends IService<AiTask> {

    /**
     * 创建任务
     */
    AiTask createTask(Long userId, String taskType, String prompt, String inputImageUrl, String options);

    /**
     * 通过 taskId 获取任务
     */
    AiTask getByTaskId(String taskId);

    /**
     * 更新任务状态
     */
    void updateStatus(String taskId, String status, String resultUrl, String errorMsg);

    /**
     * 获取用户的任务列表
     */
    Page<AiTask> getUserTasks(Long userId, Integer page, Integer size);

    /**
     * 获取统计数据
     */
    Map<String, Object> getStatistics();

    /**
     * 管理后台 - 分页查询任务列表
     */
    Page<AiTask> getAdminTaskList(Integer page, Integer size, String taskType, String status, String keyword);
}
