package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AiTask;
import com.juxin.orin.service.IAiTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 任务管理控制器 (后台管理)
 */
@RestController
@RequestMapping("/api/admin/ai-tasks")
public class AiTaskController {

    @Autowired
    private IAiTaskService aiTaskService;

    /**
     * 获取任务统计数据
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(aiTaskService.getStatistics());
    }

    /**
     * 获取任务列表 (分页)
     */
    @GetMapping("/list")
    public Result<Page<AiTask>> getTaskList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        return Result.success(aiTaskService.getAdminTaskList(page, size, taskType, status, keyword));
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    public Result<AiTask> getTask(@PathVariable String taskId) {
        AiTask task = aiTaskService.getByTaskId(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteTask(@PathVariable Long id) {
        boolean success = aiTaskService.removeById(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    /**
     * 重试失败的任务 (TODO: 实现重试逻辑)
     */
    @PostMapping("/{taskId}/retry")
    public Result<String> retryTask(@PathVariable String taskId) {
        AiTask task = aiTaskService.getByTaskId(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }
        if (!"failed".equals(task.getStatus())) {
            return Result.error("只能重试失败的任务");
        }

        // 重置状态
        aiTaskService.updateStatus(taskId, "pending", null, null);
        // TODO: 重新加入任务队列

        return Result.success("已加入重试队列");
    }
}
