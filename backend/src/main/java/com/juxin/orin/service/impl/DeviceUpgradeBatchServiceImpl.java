package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.DeviceUpgradeBatch;
import com.juxin.orin.mapper.DeviceUpgradeBatchMapper;
import com.juxin.orin.service.IDeviceUpgradeBatchService;
import org.springframework.stereotype.Service;

@Service
public class DeviceUpgradeBatchServiceImpl extends ServiceImpl<DeviceUpgradeBatchMapper, DeviceUpgradeBatch>
        implements IDeviceUpgradeBatchService {
}
