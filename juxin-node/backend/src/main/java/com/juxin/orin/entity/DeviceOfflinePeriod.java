package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_offline_period")
public class DeviceOfflinePeriod {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private LocalDateTime offlineStart;

    private LocalDateTime onlineAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
