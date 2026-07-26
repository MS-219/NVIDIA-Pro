package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备 Agent 升级包
 */
@Data
@TableName("device_upgrade_package")
public class DeviceUpgradePackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String packageNo;

    private String version;

    private String fileName;

    private String filePath;

    private String fileUrl;

    private Long fileSize;

    private String checksum;

    /** active/disabled */
    private String status;

    private String releaseNote;

    private String uploadedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
