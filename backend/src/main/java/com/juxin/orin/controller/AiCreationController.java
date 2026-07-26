package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AiTask;
import com.juxin.orin.service.IAiCreationService;
import com.juxin.orin.service.IAiTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 创作控制器
 */
@RestController
@RequestMapping("/api/ai")
public class AiCreationController {

    @Autowired
    private IAiCreationService aiCreationService;

    @Autowired
    private IAiTaskService aiTaskService;

    /**
     * 获取用户的创作任务列表
     */
    @GetMapping("/my-tasks")
    public Result<Page<AiTask>> getMyTasks(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(aiTaskService.getUserTasks(userId, page, size));
    }

    /**
     * 文生视频 (Vidu)
     */
    @PostMapping("/text-to-video")
    public Result<Object> textToVideo(@RequestBody Map<String, Object> params) {
        String prompt = (String) params.get("prompt");
        Long userId = getUserId(params);
        Integer duration = normalizeViduDuration(
                params.get("duration") != null ? Integer.valueOf(params.get("duration").toString()) : 4);
        String aspectRatio = params.get("aspectRatio") != null ? (String) params.get("aspectRatio") : "16:9";
        String resolution = params.get("resolution") != null ? (String) params.get("resolution") : "720p";

        if (prompt == null || prompt.isEmpty()) {
            return Result.error("请输入视频描述");
        }

        // 1. 立即创建本地任务，状态设为 pending
        String initialOptions = "duration=" + duration + ",aspectRatio=" + aspectRatio + ",resolution=" + resolution;
        AiTask task;
        try {
            task = aiTaskService.createTask(userId, "text-to-video", prompt, null, initialOptions);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }

        // 2. 异步调用 AI 接口获取外部 taskId
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> result = aiCreationService.textToVideo(prompt, duration, aspectRatio, resolution);
                if ((Boolean) result.get("success")) {
                    String apiTaskId = (String) result.get("taskId");
                    String updatedOptions = initialOptions + ",apiTaskId=" + apiTaskId;
                    task.setOptions(updatedOptions);
                    task.setStatus("processing");
                    aiTaskService.updateById(task);
                } else {
                    aiTaskService.updateStatus(task.getTaskId(), "failed", null, (String) result.get("error"));
                }
            } catch (Exception e) {
                aiTaskService.updateStatus(task.getTaskId(), "failed", null, "提交至 AI 引擎失败: " + e.getMessage());
            }
        });

        // 3. 立即返回本地 taskId，让前端显示“已提交”弹窗
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("taskId", task.getTaskId());
        return Result.success(response);
    }

    /**
     * 图生视频 (Vidu)
     */
    @PostMapping("/image-to-video")
    public Result<Object> imageToVideo(@RequestBody Map<String, Object> params) {
        String imageUrl = (String) params.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = (String) params.get("imageUrls");
        }
        String prompt = (String) params.get("prompt");
        Long userId = getUserId(params);
        Integer duration = normalizeViduDuration(
                params.get("duration") != null ? Integer.valueOf(params.get("duration").toString()) : 4);
        String aspectRatio = params.get("aspectRatio") != null ? (String) params.get("aspectRatio") : "16:9";
        String resolution = params.get("resolution") != null ? (String) params.get("resolution") : "720p";

        if (imageUrl == null || imageUrl.isEmpty()) {
            return Result.error("请上传图片");
        }

        // 1. 立即创建本地任务
        String initialOptions = "duration=" + duration + ",aspectRatio=" + aspectRatio + ",resolution=" + resolution;
        AiTask task;
        try {
            task = aiTaskService.createTask(userId, "image-to-video", prompt, imageUrl, initialOptions);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }

        // 2. 异步调用 AI 接口
        final String finalImageUrl = imageUrl;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> result = aiCreationService.imageToVideo(finalImageUrl, prompt, duration,
                        aspectRatio, resolution);
                if ((Boolean) result.get("success")) {
                    String apiTaskId = (String) result.get("taskId");
                    String updatedOptions = initialOptions + ",apiTaskId=" + apiTaskId;
                    task.setOptions(updatedOptions);
                    task.setStatus("processing");
                    aiTaskService.updateById(task);
                } else {
                    aiTaskService.updateStatus(task.getTaskId(), "failed", null, (String) result.get("error"));
                }
            } catch (Exception e) {
                aiTaskService.updateStatus(task.getTaskId(), "failed", null, "提交至 AI 引擎失败: " + e.getMessage());
            }
        });

        // 3. 立即返回
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("taskId", task.getTaskId());
        return Result.success(response);
    }

    /**
     * 查询视频生成状态
     */
    @GetMapping("/video-status/{taskId}")
    public Result<Object> getVideoStatus(@PathVariable String taskId) {
        // 先从数据库获取任务
        AiTask task = aiTaskService.getByTaskId(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }

        // 从 options 中获取 API taskId
        String apiTaskId = null;
        if (task.getOptions() != null && task.getOptions().contains("apiTaskId=")) {
            String[] parts = task.getOptions().split(",");
            for (String part : parts) {
                if (part.startsWith("apiTaskId=")) {
                    apiTaskId = part.substring("apiTaskId=".length());
                    break;
                }
            }
        }

        if (apiTaskId == null) {
            // 如果任务超过 1 小时还在 pending (未成功提交到 AI 引擎)，则标记为失败
            if (task.getCreateTime() != null
                    && task.getCreateTime().isBefore(java.time.LocalDateTime.now().minusHours(1))) {
                aiTaskService.updateStatus(taskId, "failed", null, "提交至 AI 引擎超时，算力已返还");
                return Result.error("任务提交超时，算力已返还");
            }

            // 如果还未获取到 apiTaskId，说明系统还在排队提交到 AI 引擎，返回 processing 状态
            Map<String, Object> pendingResult = new java.util.HashMap<>();
            pendingResult.put("status", "processing");
            pendingResult.put("success", true);
            pendingResult.put("taskId", taskId);
            return Result.success(pendingResult);
        }

        // 如果任务超过 6 小时还在生成中，直接标记为失败 (通常视频生成在 5-15 分钟内完成)
        if (task.getCreateTime() != null
                && task.getCreateTime().isBefore(java.time.LocalDateTime.now().minusHours(6))) {
            aiTaskService.updateStatus(taskId, "failed", null, "视频生成超时（已超过6小时）");
            return Result.error("生成超时，算力已返还");
        }

        Map<String, Object> result = aiCreationService.getVideoStatus(apiTaskId);

        if ((Boolean) result.get("success")) {
            String status = (String) result.get("status");
            String videoUrl = (String) result.get("videoUrl");

            // 更新数据库中的任务状态
            if ("completed".equals(status)) {
                aiTaskService.updateStatus(taskId, "completed", videoUrl, null);
                result.put("videoUrl", toPublicFileUrl(videoUrl));
            } else if ("failed".equals(status)) {
                String failReason = (String) result.get("failReason");
                aiTaskService.updateStatus(taskId, "failed", null,
                        failReason != null && !failReason.isBlank() ? failReason : "视频生成失败");
            }

            result.put("taskId", taskId);
            return Result.success(result);
        } else {
            String errorMsg = (String) result.get("error");
            // 如果查询失败，且错误提示明确（如404找不到任务），则直接标记本地任务失败并回滚算力
            if (errorMsg != null
                    && (errorMsg.contains("404") || errorMsg.contains("not found") || errorMsg.contains("查询失败"))) {
                aiTaskService.updateStatus(taskId, "failed", null, "外部任务查询异常: " + errorMsg);
            }
            return Result.error(errorMsg);
        }
    }

    /**
     * 文生图片 (GPT-Image-2)
     */
    @PostMapping("/text-to-image")
    public Result<Object> textToImage(@RequestBody Map<String, Object> params) {
        String prompt = (String) params.get("prompt");
        String size = (String) params.get("size");
        Long userId = getUserId(params);

        if (prompt == null || prompt.isEmpty()) {
            return Result.error("请输入图片描述");
        }

        Map<String, Object> response = new java.util.HashMap<>();
        String options = "size=" + (size != null ? size : "1024x1024");
        AiTask task;
        try {
            task = aiTaskService.createTask(userId, "text-to-image", prompt, null, options);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
        aiTaskService.updateStatus(task.getTaskId(), "processing", null, null);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> result = aiCreationService.textToImage(prompt, size);
                if ((Boolean) result.get("success")) {
                    String imageUrl = (String) result.get("imageUrl");
                    aiTaskService.updateStatus(task.getTaskId(), "completed", imageUrl, null);
                } else {
                    aiTaskService.updateStatus(task.getTaskId(), "failed", null, (String) result.get("error"));
                }
            } catch (Exception e) {
                aiTaskService.updateStatus(task.getTaskId(), "failed", null, e.getMessage());
            }
        });

        response.put("taskId", task.getTaskId());
        return Result.success(response);
    }

    /**
     * 以图生图 (GPT-Image-2)
     */
    @PostMapping("/image-to-image")
    public Result<Object> imageToImage(@RequestBody Map<String, Object> params) {
        String imageUrl = (String) params.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = (String) params.get("imageUrls");
        }
        String prompt = (String) params.get("prompt");
        String size = (String) params.get("size");
        Long userId = getUserId(params);

        if (imageUrl == null || imageUrl.isEmpty()) {
            return Result.error("请上传图片");
        }
        if (prompt == null || prompt.isEmpty()) {
            return Result.error("请输入描述");
        }

        Map<String, Object> response = new java.util.HashMap<>();
        AiTask task;
        try {
            task = aiTaskService.createTask(userId, "image-to-image", prompt, imageUrl,
                    "size=" + (size != null ? size : "auto"));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
        aiTaskService.updateStatus(task.getTaskId(), "processing", null, null);

        final String finalImageUrl = imageUrl;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> result = aiCreationService.imageToImage(finalImageUrl, prompt, size);
                if ((Boolean) result.get("success")) {
                    String resultImageUrl = (String) result.get("imageUrl");
                    aiTaskService.updateStatus(task.getTaskId(), "completed", resultImageUrl, null);
                } else {
                    aiTaskService.updateStatus(task.getTaskId(), "failed", null, (String) result.get("error"));
                }
            } catch (Exception e) {
                aiTaskService.updateStatus(task.getTaskId(), "failed", null, e.getMessage());
            }
        });

        response.put("taskId", task.getTaskId());
        return Result.success(response);
    }

    /**
     * AI 文案生成 (Gemini)
     */
    @PostMapping("/copywriting")
    public Result<Object> generateCopywriting(@RequestBody Map<String, Object> params) {
        String prompt = (String) params.get("prompt");
        String copyType = (String) params.get("copyType");
        String style = (String) params.get("style");
        String systemPrompt = (String) params.get("systemPrompt");
        Long userId = getUserId(params);

        if (prompt == null || prompt.isEmpty()) {
            return Result.error("请输入文案需求描述");
        }

        // 创建异步任务
        String options = "copyType=" + (copyType != null ? copyType : "general") + ",style="
                + (style != null ? style : "natural");
        AiTask task;
        try {
            task = aiTaskService.createTask(userId, "copywriting", prompt, null, options);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
        aiTaskService.updateStatus(task.getTaskId(), "processing", null, null);

        // 异步调用 AI
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> result = aiCreationService.generateCopywriting(prompt, copyType, style,
                        systemPrompt);
                if ((Boolean) result.get("success")) {
                    String content = (String) result.get("content");
                    aiTaskService.updateStatus(task.getTaskId(), "completed", content, null);
                } else {
                    aiTaskService.updateStatus(task.getTaskId(), "failed", null, (String) result.get("error"));
                }
            } catch (Exception e) {
                aiTaskService.updateStatus(task.getTaskId(), "failed", null, e.getMessage());
            }
        });

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("taskId", task.getTaskId());
        return Result.success(response);
    }

    /**
     * AI 文案生成（同步，立即返回结果）
     */
    @PostMapping("/copywriting/sync")
    public Result<Object> generateCopywritingSync(@RequestBody Map<String, Object> params) {
        String prompt = (String) params.get("prompt");
        String copyType = (String) params.get("copyType");
        String style = (String) params.get("style");
        String systemPrompt = (String) params.get("systemPrompt");
        Long userId = getUserId(params);

        if (prompt == null || prompt.isEmpty()) {
            return Result.error("请输入文案需求描述");
        }

        // 检查用户算力
        if (userId != null) {
            // 这里可以添加算力检查和扣除逻辑
        }

        // 同步调用 AI
        Map<String, Object> result = aiCreationService.generateCopywriting(prompt, copyType, style, systemPrompt);

        if ((Boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error((String) result.get("error"));
        }
    }

    private Long getUserId(Map<String, Object> params) {
        Object userIdObj = params.get("userId");
        if (userIdObj == null)
            return null;
        String userIdStr = userIdObj.toString().trim();
        if (userIdStr.isEmpty() || "null".equalsIgnoreCase(userIdStr) || "undefined".equalsIgnoreCase(userIdStr)) {
            return null;
        }
        try {
            return Long.valueOf(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer normalizeViduDuration(Integer duration) {
        if (duration == null) {
            return 4;
        }
        return Math.max(1, Math.min(duration, 16));
    }

    private String toPublicFileUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("/uploads/")) {
            return System.getenv().getOrDefault("ORIN_PUBLIC_BASE_URL", "https://orin-api.example.invalid") + url;
        }
        return url;
    }
}
