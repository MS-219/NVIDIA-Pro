package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 设备执行任务记录
 */
@Data
@TableName("ai_device_task")
public class AiDeviceTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID (系统下发的唯一任务ID)
     */
    private String taskId;

    /**
     * 任务类型: ollama, spider, social_post, etc.
     */
    private String taskType;

    /**
     * 任务参数 (JSON格式)
     */
    private String taskParams;

    /**
     * 任务任务重试次数
     */
    private Integer retryCount = 0;

    /**
     * 奖励聚芯算力值
     */

    /**
     * 执行设备SN码
     */
    private String deviceSn;

    /**
     * 任务指令内容
     */
    private String prompt;

    /**
     * 模型输出内容 (结果)
     */
    private String responseText;

    /**
     * 使用的模型型号 (例如 qwen2.5:3b)
     */
    private String modelName;

    /**
     * 任务状态: pending, running, completed, failed
     */
    private String status;

    /**
     * 生成的 token 数量
     */
    private Integer generateTokens;

    /**
     * 预计/实际奖励的算力或积分
     */
    private Integer rewardHashrate;

    /**
     * 消耗时间(毫秒)
     */
    private Long durationMs;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String deviceName;

    @TableField(exist = false)
    private String deviceIp;
}
