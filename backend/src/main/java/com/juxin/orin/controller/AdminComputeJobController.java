package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.service.IComputeJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 节点计算作业管理 (后台管理)
 */
@RestController
@RequestMapping("/api/admin/device-tasks")
public class AdminComputeJobController {

    @Autowired
    private IComputeJobService computeJobService;

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(computeJobService.getStatistics());
    }

    /**
     * 获取任务热度趋势
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend() {
        return Result.success(computeJobService.getTaskTrend());
    }

    /**
     * 获取最新任务动态
     */
    @GetMapping("/latest")
    public Result<java.util.List<ComputeJob>> getLatest(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(computeJobService.getLatestTasks(limit));
    }

    /**
     * 分发新任务
     */
    @PostMapping("/dispatch")
    public Result<String> dispatchTask(@RequestBody ComputeJob task) {
        if (task.getTaskType() == null || task.getTaskType().isBlank()) {
            return Result.error("任务类型不能为空");
        }
        task.setTaskId(UUID.randomUUID().toString());
        task.setStatus("pending");
        task.setRetryCount(0);
        task.setDeleted(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        boolean success = computeJobService.save(task);
        return success ? Result.success("任务已提交至就绪队列") : Result.error("下发失败");
    }

    @GetMapping("/list")
    public Result<Page<ComputeJob>> getTaskList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String deviceSn,
            @RequestParam(required = false) String status) {

        return Result.success(computeJobService.getAdminTaskList(page, size, deviceSn, status));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteTask(@PathVariable Long id) {
        boolean success = computeJobService.removeById(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }
}
