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

    /**
     * 处理设备心跳，并在新设备首次接入时校验镜像授权。
     */
    Device handleHeartbeat(String sn, String ip, String cpuUsage, String memoryUsage,
                           String imageLicenseKey, String imageVersion, String hardwareFingerprint,
                           String cpuModel, String agentVersion);

    boolean bindDevice(String sn, String code, Long userId);

    boolean bindDevice(String sn, String code, Long userId, Long merchantId);
}
