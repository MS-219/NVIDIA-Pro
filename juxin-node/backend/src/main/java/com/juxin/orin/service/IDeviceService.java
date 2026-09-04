package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.Device;

public interface IDeviceService extends IService<Device> {
    /**
     * 处理设备心跳
     * 
     * @param sn 设备SN
     * @param ip 设备IP
     */
    Device handleHeartbeat(String sn, String ip, String cpuUsage, String memoryUsage);

    boolean bindDevice(String sn, String code, Long userId);

    boolean bindDevice(String sn, String code, Long userId, Long merchantId);
}
