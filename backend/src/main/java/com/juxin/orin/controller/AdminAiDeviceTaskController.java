package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AiDeviceTask;
import com.juxin.orin.service.IAiDeviceTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备 AI 任务管理 (后台管理)
 */
@RestController
@RequestMapping("/api/admin/device-tasks")
public class AdminAiDeviceTaskController {

    @Autowired
    private IAiDeviceTaskService aiDeviceTaskService;

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(aiDeviceTaskService.getStatistics());
    }

    /**
     * 获取任务热度趋势
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend() {
        return Result.success(aiDeviceTaskService.getTaskTrend());
    }

    /**
     * 获取最新任务动态
     */
    @GetMapping("/latest")
    public Result<java.util.List<AiDeviceTask>> getLatest(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(aiDeviceTaskService.getLatestTasks(limit));
    }

    /**
     * 分发新任务
     */
    @PostMapping("/dispatch")
    public Result<String> dispatchTask(@RequestBody AiDeviceTask task) {
        task.setStatus("pending");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        boolean success = aiDeviceTaskService.save(task);
        return success ? Result.success("任务已提交至就绪队列") : Result.error("下发失败");
    }

    @GetMapping("/list")
    public Result<Page<AiDeviceTask>> getTaskList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String deviceSn,
            @RequestParam(required = false) String status) {

        return Result.success(aiDeviceTaskService.getAdminTaskList(page, size, deviceSn, status));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteTask(@PathVariable Long id) {
        boolean success = aiDeviceTaskService.removeById(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }
}
