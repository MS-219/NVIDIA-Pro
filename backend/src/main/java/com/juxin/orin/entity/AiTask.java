package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 创作任务实体
 */
@Data
@TableName("ai_task")
public class AiTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID (UUID, 用于API查询)
     */
    private String taskId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 任务类型: text-to-video, image-to-video, text-to-image, image-to-image
     */
    private String taskType;

    /**
     * 提示词/描述
     */
    private String prompt;

    /**
     * 输入图片URL (图生视频/以图生图)
     */
    private String inputImageUrl;

    /**
     * 视频时长(秒) 或 图片尺寸
     */
    private String options;

    /**
     * 任务状态: pending, processing, completed, failed
     */
    private String status;

    /**
     * 结果URL (视频或图片)
     */
    private String resultUrl;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 消耗的配额/积分
     */
    private Integer costQuota;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    // ========== 非数据库字段 ==========

    /** 用户昵称 */
    @TableField(exist = false)
    private String nickname;

    /** 用户头像 */
    @TableField(exist = false)
    private String avatarUrl;
}
