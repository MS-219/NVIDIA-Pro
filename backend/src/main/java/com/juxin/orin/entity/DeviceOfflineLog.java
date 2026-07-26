package com.juxin.orin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("device_offline_log")
public class DeviceOfflineLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private String sn;

    private String bindCode;

    private LocalDateTime offlineTime;

    private LocalDateTime lastHeartbeatTime;

    private String reason;

    private LocalDateTime createTime;
}
