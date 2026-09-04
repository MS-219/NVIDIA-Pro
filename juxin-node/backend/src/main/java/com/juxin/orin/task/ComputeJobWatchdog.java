package com.juxin.orin.task;

import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.service.IComputeJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 算力任务巡检卫士
 * 处理超时任务并进行自动重平衡（将卡死的任务重新分配给其他节点）
 */
@Component
public class ComputeJobWatchdog {

    private static final Logger log = LoggerFactory.getLogger(ComputeJobWatchdog.class);

    @Autowired
    private IComputeJobService computeJobService;

    /**
     * 每分钟执行一次扫描
     * 找出超时未响应的 running 状态任务
     */
    @Scheduled(fixedRate = 60000)
    public void checkTimeoutTasks() {
        // 超时阈值：5 分钟（如果一个任务运行中，且 5 分钟没更新过，视为异常）
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(5);

        List<ComputeJob> timeoutTasks = computeJobService.lambdaQuery()
                .eq(ComputeJob::getStatus, "running")
                .lt(ComputeJob::getUpdateTime, timeoutThreshold)
                .list();

        if (timeoutTasks.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时推理任务，开始重平衡逻辑...", timeoutTasks.size());

        for (ComputeJob task : timeoutTasks) {
            try {
                handleTaskRebalance(task);
            } catch (Exception e) {
                log.error("处理任务重平衡失败: taskId={}, error={}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * 执行重平衡：重置状态或标记失败
     */
    private void handleTaskRebalance(ComputeJob task) {
        int retries = task.getRetryCount() != null ? task.getRetryCount() : 0;

        if (retries < 3) {
            // 策略：重回队列，清除当前绑定的 SN，让其他 Agent 捡走
            log.warn("任务 ID {} 执行超时 (节点: {})，由巡检卫士重置回 pending 池。当前重试次数: {}",
                    task.getId(), task.getDeviceSn(), retries);

            String originalDeviceSn = task.getDeviceSn();
            task.setStatus("pending");
            task.setDeviceSn(null); // 释放归属
            task.setRetryCount(retries + 1);
            task.setUpdateTime(LocalDateTime.now());
            task.setErrorMsg("节点超时，系统自动执行重平衡。原节点: " + originalDeviceSn);

            computeJobService.updateById(task);
        } else {
            // 策略：重试次数耗尽，彻底标记为失败
            log.error("任务 ID {} 已达到最大重试上限 ({})，标记为最终失败。", task.getId(), retries);

            task.setStatus("failed");
            task.setErrorMsg("节点多次响应超时，已放弃重试。");
            task.setUpdateTime(LocalDateTime.now());

            computeJobService.updateById(task);
        }
    }
}
