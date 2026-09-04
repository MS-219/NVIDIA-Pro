package com.juxin.orin.controller;

import com.juxin.orin.service.IBossKgService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * 佣金保回调控制器
 * 接收佣金保平台的异步通知
 */
@Slf4j
@RestController
@RequestMapping("/api/bosskg/notify")
public class BossKgNotifyController {

    @Autowired
    private IBossKgService bossKgService;

    /**
     * 签约结果回调
     * 佣金保平台在签约成功时会回调此接口
     */
    @PostMapping(value = "/contract", produces = MediaType.TEXT_PLAIN_VALUE)
    public String contractNotify(@RequestBody String requestBody) {
        log.info("收到签约回调");
        try {
            return bossKgService.handleContractNotify(requestBody);
        } catch (Exception e) {
            log.error("处理签约回调异常", e);
            return "FAIL";
        }
    }

    /**
     * 付款结果回调
     * 佣金保平台在付款成功/失败时会回调此接口
     */
    @PostMapping(value = "/payment", produces = MediaType.TEXT_PLAIN_VALUE)
    public String paymentNotify(@RequestBody String requestBody) {
        log.info("收到付款回调");
        try {
            return bossKgService.handlePaymentNotify(requestBody);
        } catch (Exception e) {
            log.error("处理付款回调异常", e);
            return "FAIL";
        }
    }
}
