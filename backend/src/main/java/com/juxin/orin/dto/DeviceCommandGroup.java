package com.juxin.orin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备指令任务聚合视图。
 */
@Data
public class DeviceCommandGroup {

    private String groupKey;

    private String commandType;

    private String commandText;

    private String remark;

    private String sampleCommandNo;

    private String sampleDeviceSn;

    private Long totalCount;

    private Long pendingCount;

    private Long deliveredCount;

    private Long completedCount;

    private Long failedCount;

    private Long canceledCount;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdateTime;
}
