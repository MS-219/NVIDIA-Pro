package com.juxin.orin.task;

import com.juxin.orin.entity.AiTask;
import com.juxin.orin.service.IAiCreationService;
import com.juxin.orin.service.IAiTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 任务状态检查定时任务
 * 自动检查正在处理中的视频任务，更新其状态
 * 这样即使用户退出页面，任务也能在后台继续更新
 */
@Component
public class AiTaskStatusTask {

    private static final Logger log = LoggerFactory.getLogger(AiTaskStatusTask.class);
    private static final long SUBMISSION_TIMEOUT_MINUTES = 60;

    @Autowired
    private IAiTaskService aiTaskService;

    @Autowired
    private IAiCreationService aiCreationService;

    /**
     * 每30秒检查一次处理中的视频任务
     */
    @Scheduled(fixedRate = 30000)
    public void checkPendingVideoTasks() {
        // 查询所有状态为 processing, queued, pending 的视频任务
        List<AiTask> processingTasks = aiTaskService.lambdaQuery()
                .in(AiTask::getStatus, "processing", "queued", "pending")
                .and(wrapper -> wrapper
                        .eq(AiTask::getTaskType, "text-to-video")
                        .or()
                        .eq(AiTask::getTaskType, "image-to-video"))
                .list();

        if (processingTasks.isEmpty()) {
            return;
        }

        log.info("检查 {} 个处理中的视频任务...", processingTasks.size());

        for (AiTask task : processingTasks) {
            try {
                checkAndUpdateTaskStatus(task);
            } catch (Exception e) {
                log.error("检查任务状态失败: taskId={}, error={}", task.getTaskId(), e.getMessage());
            }
        }
    }

    /**
     * 检查并更新单个任务的状态
     */
    private void checkAndUpdateTaskStatus(AiTask task) {
        long minutesSinceCreation = getMinutesSinceCreation(task);

        // 从 options 中获取 API taskId
        String apiTaskId = extractApiTaskId(task.getOptions());
        if (apiTaskId == null) {
            // 没有外部任务 ID 的任务无法被继续轮询，超过提交超时后直接失败并停止刷日志
            if (minutesSinceCreation > SUBMISSION_TIMEOUT_MINUTES) {
                aiTaskService.updateStatus(task.getTaskId(), "failed", null, "提交至 AI 引擎超时，算力已返还");
                log.info("任务 {} 缺少 apiTaskId 且已超时（{}分钟），标记失败", task.getTaskId(), minutesSinceCreation);
            }
            return;
        }

        // 检查任务是否超过30分钟
        if (minutesSinceCreation > 30) {
            aiTaskService.updateStatus(task.getTaskId(), "failed", null, "生成超时（超过30分钟）");
            log.info("任务 {} 超时失败（{}分钟）", task.getTaskId(), minutesSinceCreation);
            return;
        }

        // 调用 API 查询状态
        try {
            Map<String, Object> result = aiCreationService.getVideoStatus(apiTaskId);

            if (result.get("success") != null && (Boolean) result.get("success")) {
                String status = (String) result.get("status");
                String videoUrl = (String) result.get("videoUrl");

                if ("completed".equals(status)) {
                    aiTaskService.updateStatus(task.getTaskId(), "completed", videoUrl, null);
                    log.info("任务 {} 已完成，视频URL: {}", task.getTaskId(), videoUrl);
                } else if ("failed".equals(status)) {
                    // 获取失败原因
                    String failReason = (String) result.get("failReason");
                    if (failReason == null || failReason.isEmpty()) {
                        failReason = "视频生成失败";
                    }
                    aiTaskService.updateStatus(task.getTaskId(), "failed", null, failReason);
                    log.info("任务 {} 生成失败: {}", task.getTaskId(), failReason);
                }
                // 其他状态（processing等）不做任何操作，继续等待
            }
            // API 调用失败不标记任务失败，下次继续检查
        } catch (Exception e) {
            log.warn("查询任务 {} 状态时出错: {}，将在下次检查时重试", task.getTaskId(), e.getMessage());
        }
    }

    /**
     * 从 options 字符串中提取 apiTaskId
     */
    private String extractApiTaskId(String options) {
        if (options == null || !options.contains("apiTaskId=")) {
            return null;
        }
        String[] parts = options.split(",");
        for (String part : parts) {
            if (part.startsWith("apiTaskId=")) {
                return part.substring("apiTaskId=".length());
            }
        }
        return null;
    }

    private long getMinutesSinceCreation(AiTask task) {
        if (task.getCreateTime() == null) {
            return 0;
        }
        return java.time.Duration.between(task.getCreateTime(), java.time.LocalDateTime.now()).toMinutes();
    }
}
