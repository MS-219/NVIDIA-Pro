package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.DeviceEarnings;

import java.math.BigDecimal;
import java.util.Map;

public interface IDeviceEarningsService extends IService<DeviceEarnings> {

    /**
     * 获取用户收益统计
     * 
     * @param userId 用户ID（null 表示全部用户）
     * @return { yesterday, total }
     */
    Map<String, BigDecimal> getUserEarnings(Long userId);

    /**
     * 获取系统总收益统计
     */
    Map<String, BigDecimal> getSystemEarnings();

    /**
     * 为设备生成每日收益（定时任务调用）
     */
    void generateDailyEarnings();

    /**
     * 补偿收益：为所有已绑定设备补发指定天数的收益和算力值
     * 用于服务器宕机等异常情况下的收益补偿
     *
     * @param days 补偿天数
     * @return 补偿结果统计 { successCount, failCount, totalAmount }
     */
    Map<String, Object> compensateEarnings(int days);
}
