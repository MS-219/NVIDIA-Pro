package com.juxin.orin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DeviceEarningsDailyDto {
    private Long deviceId;
    private String sn;
    private String bindCode;
    private Integer status;
    private LocalDateTime lastHeartbeatTime;
    private LocalDateTime bindTime;
    private LocalDate date;
    private BigDecimal amount;
}
