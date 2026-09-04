package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.DeviceOfflineLog;
import com.juxin.orin.service.IDeviceOfflineLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/device-offline-log")
public class DeviceOfflineLogController {

    @Autowired
    private IDeviceOfflineLogService offlineLogService;

    /**
     * 分页查询离线日志
     */
    @GetMapping("/list")
    public Result<Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String sn) {

        Page<DeviceOfflineLog> pageParam = new Page<>(page, size);

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeviceOfflineLog> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        if (sn != null && !sn.trim().isEmpty()) {
            wrapper.and(w -> w.like(DeviceOfflineLog::getSn, sn)
                    .or()
                    .like(DeviceOfflineLog::getBindCode, sn));
        }

        wrapper.orderByDesc(DeviceOfflineLog::getCreateTime);

        return Result.success(offlineLogService.page(pageParam, wrapper));
    }
}
