package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备远程指令记录
 */
@Data
@TableName("device_command")
public class DeviceCommand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String commandNo;

    private Long deviceId;

    private String deviceSn;

    private String commandType;

    private String commandText;

    private String commandPayload;

    /** pending/delivered/completed/canceled/failed */
    private String status;

    private Integer exitCode;

    private String resultText;

    private String remark;

    private LocalDateTime dispatchedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
