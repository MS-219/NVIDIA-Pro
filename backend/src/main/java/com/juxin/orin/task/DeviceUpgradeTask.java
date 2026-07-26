package com.juxin.orin.task;

import com.juxin.orin.service.IDeviceUpgradeService;
import com.juxin.orin.service.ISystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeviceUpgradeTask {

    @Autowired
    private IDeviceUpgradeService deviceUpgradeService;

    @Autowired
    private ISystemConfigService configService;

    @Scheduled(fixedRate = 60000)
    public void markUpgradeTimeouts() {
        int timeoutMinutes = Integer.parseInt(configService.getConfig("device.upgradeTimeoutMinutes", "30"));
        deviceUpgradeService.markTimeoutRecords(timeoutMinutes);
    }
}
