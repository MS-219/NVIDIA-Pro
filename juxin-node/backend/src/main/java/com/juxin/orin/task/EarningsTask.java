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
     * 每分钟检查一次，结算已结束的上一个自然日。
     * 服务在零点后启动时也能自动补上当日结算。
     */
    @Scheduled(fixedRate = 60000)
    public void calculateDailyEarnings() {
        earningsService.generateDailyEarnings();
    }

}
