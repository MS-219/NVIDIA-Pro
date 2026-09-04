package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceUpgradeBatch;
import com.juxin.orin.entity.DeviceUpgradePackage;
import com.juxin.orin.entity.DeviceUpgradeRecord;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IDeviceUpgradeBatchService;
import com.juxin.orin.service.IDeviceUpgradePackageService;
import com.juxin.orin.service.IDeviceUpgradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/device-upgrades")
public class AdminDeviceUpgradeController {

    @Autowired
    private IDeviceUpgradeService upgradeService;

    @Autowired
    private IDeviceUpgradePackageService packageService;

    @Autowired
    private IDeviceUpgradeBatchService batchService;

    @Autowired
    private IDeviceService deviceService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(upgradeService.getStats());
    }

    @PostMapping("/packages/upload")
    public Result<DeviceUpgradePackage> uploadPackage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version,
            @RequestParam(value = "releaseNote", required = false) String releaseNote,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy) {
        try {
            return Result.success(upgradeService.uploadPackage(file, version, releaseNote, uploadedBy));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/packages")
    public Result<IPage<DeviceUpgradePackage>> packages(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String version) {
        LambdaQueryWrapper<DeviceUpgradePackage> wrapper = new LambdaQueryWrapper<>();
        if (version != null && !version.trim().isEmpty()) {
            wrapper.like(DeviceUpgradePackage::getVersion, version.trim());
        }
        wrapper.orderByDesc(DeviceUpgradePackage::getCreateTime);
        return Result.success(packageService.page(new Page<>(page, size), wrapper));
    }

    @PostMapping("/packages/disable")
    public Result<String> disablePackage(@RequestBody Map<String, Object> params) {
        Long id = getLong(params, "id");
        if (id == null) {
            return Result.error("id 不能为空");
        }
        boolean success = packageService.lambdaUpdate()
                .eq(DeviceUpgradePackage::getId, id)
                .set(DeviceUpgradePackage::getStatus, "disabled")
                .update();
        return success ? Result.success("已停用") : Result.error("停用失败");
    }

    @GetMapping("/target-count")
    public Result<Map<String, Object>> targetCount(
            @RequestParam(required = false) String targetScope,
            @RequestParam(required = false) String locationKeyword,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String targetVersion,
            @RequestParam(defaultValue = "true") Boolean onlyOutdated) {
        Map<String, Object> data = new HashMap<>();
        data.put("count", countTargetDevices(targetScope, locationKeyword, carrier, targetVersion, onlyOutdated));
        return Result.success(data);
    }

    @PostMapping("/batches")
    public Result<DeviceUpgradeBatch> createBatch(@RequestBody Map<String, Object> params) {
        try {
            return Result.success(upgradeService.createBatch(params));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("创建升级批次失败: " + e.getMessage());
        }
    }

    @GetMapping("/batches")
    public Result<IPage<DeviceUpgradeBatch>> batches(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<DeviceUpgradeBatch> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(DeviceUpgradeBatch::getStatus, status.trim());
        }
        wrapper.orderByDesc(DeviceUpgradeBatch::getCreateTime);
        return Result.success(batchService.page(new Page<>(page, size), wrapper));
    }

    @GetMapping("/records")
    public Result<IPage<DeviceUpgradeRecord>> records(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String deviceSn,
            @RequestParam(required = false) String status) {
        return Result.success(upgradeService.listRecords(page, size, batchId, deviceSn, status));
    }

    private long countTargetDevices(String targetScope, String locationKeyword, String carrier,
                                    String targetVersion, Boolean onlyOutdated) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getStatus, 1)
                .eq(Device::getType, 2);
        if ("LOCATION".equals(targetScope)) {
            if (locationKeyword == null || locationKeyword.trim().isEmpty()) {
                return 0L;
            }
            wrapper.like(Device::getLocation, locationKeyword.trim());
        } else if ("CARRIER".equals(targetScope)) {
            if (carrier == null || carrier.trim().isEmpty()) {
                return 0L;
            }
            wrapper.eq(Device::getCarrier, carrier.trim());
        } else if (targetScope != null && !"ALL_ONLINE".equals(targetScope)) {
            return 0L;
        }
        if (Boolean.TRUE.equals(onlyOutdated) && targetVersion != null && !targetVersion.trim().isEmpty()) {
            wrapper.and(w -> w.isNull(Device::getAgentVersion).or().ne(Device::getAgentVersion, targetVersion.trim()));
        }
        return deviceService.count(wrapper);
    }

    private Long getLong(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Long.valueOf(value.toString());
    }
}
