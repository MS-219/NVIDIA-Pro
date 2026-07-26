package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.AiTask;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.mapper.AiTaskMapper;
import com.juxin.orin.service.IAiTaskService;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.ISystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 任务服务实现
 */
@Slf4j
@Service
public class AiTaskServiceImpl extends ServiceImpl<AiTaskMapper, AiTask> implements IAiTaskService {

    @Autowired
    private IAppUserService appUserService;

    @Autowired
    private ISystemConfigService configService;

    // Config keys (matching SettingsController)
    private static final String KEY_IMAGE_GEN_COST = "ai.imageGenCost";
    private static final String KEY_IMAGE_TO_VIDEO_COST = "ai.imageToVideoCost";
    private static final String KEY_VIDEO_GEN_COST = "ai.videoGenCost";
    private static final String KEY_VIDEO_EXTRA_4 = "ai.videoExtra4s";
    private static final String KEY_VIDEO_EXTRA_10 = "ai.videoExtra10s";
    private static final String KEY_VIDEO_EXTRA_15 = "ai.videoExtra15s";
    private static final String KEY_VIDEO_EXTRA_25 = "ai.videoExtra25s";

    @Override
    @Transactional
    public AiTask createTask(Long userId, String taskType, String prompt, String inputImageUrl, String options) {
        // 1. 根据任务类型计算消耗配额
        int cost = 1;
        try {
            if (taskType.contains("video")) {
                if (taskType.equals("image-to-video")) {
                    cost = getIntConfig(KEY_IMAGE_TO_VIDEO_COST, 10);
                } else {
                    cost = getIntConfig(KEY_VIDEO_GEN_COST, 10);
                }
                // 检查时长额外消耗
                if (options != null && options.contains("duration=")) {
                    if (options.contains("duration=10"))
                        cost += getIntConfig(KEY_VIDEO_EXTRA_10, 5);
                    else if (options.contains("duration=15"))
                        cost += getIntConfig(KEY_VIDEO_EXTRA_15, 10);
                    else if (options.contains("duration=25"))
                        cost += getIntConfig(KEY_VIDEO_EXTRA_25, 20);
                    else if (options.contains("duration=4"))
                        cost += getIntConfig(KEY_VIDEO_EXTRA_4, 0);
                }
            } else if (taskType.contains("image")) {
                cost = getIntConfig(KEY_IMAGE_GEN_COST, 2);
            }
        } catch (Exception e) {
            log.warn("Parsing AI cost config failed, using default: {}", e.getMessage());
        }

        // 2. 校验用户余额是否充足
        if (userId != null) {
            AppUser user = appUserService.getById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
            if (user.getQuota() == null) {
                user.setQuota(0);
            }
            if (user.getQuota() < cost) {
                log.warn("User {} quota insufficient. Has: {}, Needs: {}", userId, user.getQuota(), cost);
                throw new RuntimeException("算力不足，无法完成任务");
            }

            // 3. 执行扣费 (同时扣除余额和配额)
            int hashrateRate = getIntConfig("earnings.hashratePerYuan", 100);
            java.math.BigDecimal balanceDeduction = new java.math.BigDecimal(cost)
                    .divide(new java.math.BigDecimal(hashrateRate), 2, java.math.RoundingMode.HALF_UP);

            user.setQuota(user.getQuota() - cost);
            if (user.getBalance() == null) {
                user.setBalance(java.math.BigDecimal.ZERO);
            }
            // 确保余额不为负
            if (user.getBalance().compareTo(balanceDeduction) >= 0) {
                user.setBalance(user.getBalance().subtract(balanceDeduction));
            } else {
                user.setBalance(java.math.BigDecimal.ZERO);
            }

            appUserService.updateById(user);
        }

        // 4. 保存任务记录
        AiTask task = new AiTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setPrompt(prompt);
        task.setInputImageUrl(inputImageUrl);
        task.setOptions(options);
        task.setStatus("pending");
        task.setCreateTime(LocalDateTime.now());
        task.setCostQuota(cost);

        this.save(task);
        return task;
    }

    @Override
    public AiTask getByTaskId(String taskId) {
        return this.lambdaQuery()
                .eq(AiTask::getTaskId, taskId)
                .one();
    }

    @Override
    @Transactional
    public void updateStatus(String taskId, String status, String resultUrl, String errorMsg) {
        AiTask task = getByTaskId(taskId);
        if (task != null) {
            String oldStatus = task.getStatus();
            task.setStatus(status);

            // 如果任务成功且有结果URL，尝试下载文件到本地服务器
            if ("completed".equals(status) && resultUrl != null && resultUrl.startsWith("http")) {
                try {
                    String localUrl = downloadAndSaveFile(resultUrl, task.getTaskType());
                    if (localUrl != null) {
                        log.info("Successfully downloaded AI result to local: {} -> {}", resultUrl, localUrl);
                        // 使用本地URL替换原始URL
                        resultUrl = localUrl;
                    }
                } catch (Exception e) {
                    log.error("Failed to download AI result file: " + resultUrl, e);
                    // 下载失败，仍然使用原始链接，不影响主流程
                }
            }

            task.setResultUrl(resultUrl);
            task.setErrorMsg(errorMsg);

            if ("completed".equals(status) || "failed".equals(status)) {
                task.setCompleteTime(LocalDateTime.now());

                // 如果任务失败且之前不是失败状态，返还算力给用户
                if ("failed".equals(status) && !"failed".equals(oldStatus) && task.getCostQuota() != null
                        && task.getCostQuota() > 0) {
                    AppUser user = appUserService.getById(task.getUserId());
                    if (user != null) {
                        int beforeQuota = user.getQuota() != null ? user.getQuota() : 0;
                        user.setQuota(beforeQuota + task.getCostQuota());

                        // 同步返还余额
                        int hashrateRate = getIntConfig("earnings.hashratePerYuan", 100);
                        java.math.BigDecimal balanceRefund = new java.math.BigDecimal(task.getCostQuota())
                                .divide(new java.math.BigDecimal(hashrateRate), 2, java.math.RoundingMode.HALF_UP);
                        if (user.getBalance() == null) {
                            user.setBalance(java.math.BigDecimal.ZERO);
                        }
                        user.setBalance(user.getBalance().add(balanceRefund));

                        appUserService.updateById(user);
                        log.info("Task {} failed, refunding {} quota and {} balance to user {}. Final quota: {}",
                                taskId, task.getCostQuota(), balanceRefund, task.getUserId(), user.getQuota());
                    }
                }
            }
            this.updateById(task);
        }
    }

    /**
     * 下载远程文件到本地 uploads 目录
     */
    private String downloadAndSaveFile(String remoteUrl, String taskType) {
        try {
            // 1. 确定文件后缀
            String suffix = ".jpg";
            if (taskType != null && taskType.contains("video")) {
                suffix = ".mp4";
            } else if (remoteUrl.contains(".")) {
                // 尝试从URL中获取后缀，但这可能不准确
                String temp = remoteUrl.substring(remoteUrl.lastIndexOf("."));
                if (temp.length() <= 5 && temp.matches("\\.[a-zA-Z0-9]+")) {
                    suffix = temp;
                }
            }

            // 2. 准备本地目录
            String uploadPath = System.getProperty("user.dir") + "/uploads/";
            java.io.File dir = new java.io.File(uploadPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 3. 生成本地文件名和路径
            String fileName = "ai_" + UUID.randomUUID().toString().replace("-", "") + suffix;
            java.io.File destFile = new java.io.File(uploadPath + fileName);

            // 4. 执行下载
            java.net.URL url = java.net.URI.create(remoteUrl).toURL();
            try (java.io.InputStream in = url.openStream()) {
                java.nio.file.Files.copy(in, destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 5. 构建访问URL
            // 优先读取配置的域名，如果没有配置，尝试获取本机IP，实在不行用相对路径
            String fileBaseUrl = configService.getConfig("system.fileBaseUrl");

            if (fileBaseUrl != null && !fileBaseUrl.isEmpty()) {
                // 确保配置的URLBase以/结尾
                if (!fileBaseUrl.endsWith("/"))
                    fileBaseUrl += "/";
                return fileBaseUrl + "uploads/" + fileName;
            } else {
                String publicBase = System.getenv().getOrDefault(
                        "ORIN_PUBLIC_BASE_URL",
                        "https://orin-api.example.invalid");
                return publicBase + "/uploads/" + fileName;
            }
        } catch (Exception e) {
            log.error("Download file error", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<AiTask> getUserTasks(Long userId, Integer page, Integer size) {
        Page<AiTask> pageParam = new Page<>(page, size);
        return this.lambdaQuery()
                .eq(AiTask::getUserId, userId)
                .orderByDesc(AiTask::getCreateTime)
                .page(pageParam);
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 总任务数
        stats.put("totalTasks", this.count());

        // 今日任务数
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long todayTasks = this.lambdaQuery()
                .ge(AiTask::getCreateTime, today)
                .count();
        stats.put("todayTasks", todayTasks);

        // 各状态数量
        long pendingCount = this.lambdaQuery().eq(AiTask::getStatus, "pending").count();
        long processingCount = this.lambdaQuery().eq(AiTask::getStatus, "processing").count();
        long completedCount = this.lambdaQuery().eq(AiTask::getStatus, "completed").count();
        long failedCount = this.lambdaQuery().eq(AiTask::getStatus, "failed").count();

        stats.put("pendingCount", pendingCount);
        stats.put("processingCount", processingCount);
        stats.put("completedCount", completedCount);
        stats.put("failedCount", failedCount);

        // 各类型数量
        long textToVideoCount = this.lambdaQuery().eq(AiTask::getTaskType, "text-to-video").count();
        long imageToVideoCount = this.lambdaQuery().eq(AiTask::getTaskType, "image-to-video").count();
        long textToImageCount = this.lambdaQuery().eq(AiTask::getTaskType, "text-to-image").count();
        long imageToImageCount = this.lambdaQuery().eq(AiTask::getTaskType, "image-to-image").count();

        stats.put("textToVideoCount", textToVideoCount);
        stats.put("imageToVideoCount", imageToVideoCount);
        stats.put("textToImageCount", textToImageCount);
        stats.put("imageToImageCount", imageToImageCount);

        return stats;
    }

    @Override
    public Page<AiTask> getAdminTaskList(Integer page, Integer size, String taskType, String status, String keyword) {
        Page<AiTask> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<>();

        if (taskType != null && !taskType.isEmpty()) {
            wrapper.eq(AiTask::getTaskType, taskType);
        }

        if (status != null && !status.isEmpty()) {
            wrapper.eq(AiTask::getStatus, status);
        }

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(AiTask::getPrompt, keyword)
                    .or().like(AiTask::getTaskId, keyword));
        }

        wrapper.orderByDesc(AiTask::getCreateTime);

        Page<AiTask> result = this.page(pageParam, wrapper);

        // 填充用户信息
        for (AiTask record : result.getRecords()) {
            if (record.getUserId() != null) {
                AppUser user = appUserService.getById(record.getUserId());
                if (user != null) {
                    record.setNickname(user.getNickname());
                    record.setAvatarUrl(user.getAvatarUrl());
                }
            }
        }

        return result;
    }

    private int getIntConfig(String key, int defaultValue) {
        try {
            String val = configService.getConfig(key);
            if (val == null || val.trim().isEmpty())
                return defaultValue;
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
