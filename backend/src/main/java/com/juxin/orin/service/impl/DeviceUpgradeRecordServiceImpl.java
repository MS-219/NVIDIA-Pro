package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.DeviceUpgradeRecord;
import com.juxin.orin.mapper.DeviceUpgradeRecordMapper;
import com.juxin.orin.service.IDeviceUpgradeRecordService;
import org.springframework.stereotype.Service;

@Service
public class DeviceUpgradeRecordServiceImpl extends ServiceImpl<DeviceUpgradeRecordMapper, DeviceUpgradeRecord>
        implements IDeviceUpgradeRecordService {
}
