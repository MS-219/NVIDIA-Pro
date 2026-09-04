package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备升级批次
 */
@Data
@TableName("device_upgrade_batch")
public class DeviceUpgradeBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private Long packageId;

    private String targetVersion;

    private String targetScope;

    private String locationKeyword;

    private String carrier;

    private Integer totalCount;

    private Integer pendingCount;

    private Integer deliveredCount;

    private Integer upgradingCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer skippedCount;

    /** pending/running/completed/failed/partial */
    private String status;

    private String remark;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
