package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("image_license")
public class ImageLicense {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String licenseKey;

    private String name;

    private String imageVersion;

    /** active/revoked */
    private String status;

    private String remark;

    private String createdBy;

    /** 绑定的工厂账号用户名 */
    private String factoryUsername;

    private LocalDateTime revokedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Long activationCount;

    @TableField(exist = false)
    private LocalDateTime lastSeenAt;
}
