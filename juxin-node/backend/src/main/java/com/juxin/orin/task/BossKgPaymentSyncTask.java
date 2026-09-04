package com.juxin.orin.task;

import com.juxin.orin.entity.Withdraw;
import com.juxin.orin.service.IBossKgService;
import com.juxin.orin.service.IWithdrawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 主动查询佣金保非终态订单，作为异步通知丢失时的兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BossKgPaymentSyncTask {

    private final IBossKgService bossKgService;
    private final IWithdrawService withdrawService;

    @Scheduled(fixedDelayString = "${bosskg.sync-interval-ms:120000}", initialDelayString = "${bosskg.sync-initial-delay-ms:30000}")
    public void syncPendingPayments() {
        if (!bossKgService.isEnabled()) {
            return;
        }

        List<Withdraw> pending = withdrawService.lambdaQuery()
                .in(Withdraw::getBossKgState, 1, 6)
                .isNotNull(Withdraw::getBossKgBatchId)
                .last("LIMIT 200")
                .list();

        for (Withdraw withdraw : pending) {
            try {
                withdrawService.syncBossKgStatus(withdraw.getId());
            } catch (Exception e) {
                log.warn("自动同步佣金保付款状态失败 - withdrawId:{}", withdraw.getId(), e);
            }
        }
    }
}
