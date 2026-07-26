package com.juxin.orin.task;

import com.juxin.orin.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 用户等级自动更新任务
 */
@Component
public class UserLevelTask {

    @Autowired
    private IAppUserService appUserService;

    /**
     * 每 10 分钟更新一次所有用户的等级
     */
    @Scheduled(fixedRate = 600000)
    public void updateUserLevels() {
        appUserService.updateAllUserLevels();
    }
}
