package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.DeviceUpgradePackage;
import com.juxin.orin.mapper.DeviceUpgradePackageMapper;
import com.juxin.orin.service.IDeviceUpgradePackageService;
import org.springframework.stereotype.Service;

@Service
public class DeviceUpgradePackageServiceImpl extends ServiceImpl<DeviceUpgradePackageMapper, DeviceUpgradePackage>
        implements IDeviceUpgradePackageService {
}
