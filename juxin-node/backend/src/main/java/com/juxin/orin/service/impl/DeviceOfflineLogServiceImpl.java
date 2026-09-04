package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.DeviceOfflineLog;
import com.juxin.orin.mapper.DeviceOfflineLogMapper;
import com.juxin.orin.service.IDeviceOfflineLogService;
import org.springframework.stereotype.Service;

@Service
public class DeviceOfflineLogServiceImpl extends ServiceImpl<DeviceOfflineLogMapper, DeviceOfflineLog>
        implements IDeviceOfflineLogService {
}
