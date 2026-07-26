package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈实体
 */
@Data
@TableName("user_feedback")
public class UserFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 反馈用户ID */
    private Long userId;

    /** 反馈类型: suggestion-功能建议, bug-问题反馈, complaint-投诉建议, other-其他 */
    private String type;

    /** 反馈内容 */
    private String content;

    /** 联系方式 */
    private String contact;

    /** 截图URL，多个用逗号分隔 */
    private String images;

    /** 处理状态: 0-待处理, 1-处理中, 2-已处理 */
    private Integer status;

    /** 回复内容 */
    private String reply;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ========== 非数据库字段 ==========

    /** 用户昵称 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String nickname;

    /** 用户头像 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String avatarUrl;
}
