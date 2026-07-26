package com.juxin.orin.task;

import com.juxin.orin.service.IDeviceEarningsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 收益计算定时任务
 */
@Component
public class EarningsTask {

    @Autowired
    private IDeviceEarningsService earningsService;

    /**
     * 每分钟检查一次收益结算
     * 采用滚动时长制：检查每台设备是否已运行满 1 小时
     */
    @Scheduled(fixedRate = 60000)
    public void calculateHourlyEarnings() {
        earningsService.generateHourlyEarnings();
    }

}
