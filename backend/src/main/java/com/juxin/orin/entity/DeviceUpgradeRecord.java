package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单台设备升级明细
 */
@Data
@TableName("device_upgrade_record")
public class DeviceUpgradeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private Long packageId;

    private Long commandId;

    private String commandNo;

    private Long deviceId;

    private String deviceSn;

    private String fromVersion;

    private String targetVersion;

    /** pending/delivered/upgrading/success/failed/skipped/timeout */
    private String status;

    private String errorMsg;

    private String resultText;

    private LocalDateTime deliveredAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
