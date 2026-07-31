package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.service.IComputeJobService;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 算力平台：设备管理控制器
 * 展示真实设备（排除虚拟设备 type=1）
 */
@RestController
@RequestMapping("/api/admin/sl/devices")
public class AdminOrinDeviceController {

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IComputeJobService computeJobService;

    @Autowired
    private IDeviceCommandService deviceCommandService;

    /**
     * 获取设备统计摘要（排除虚拟设备 type=1）
     */
    @GetMapping("/stats")
    public Result<Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        LambdaQueryWrapper<Device> baseWrapper = buildDashboardDeviceWrapper();

        // 总设备数
        long totalCount = deviceService.count(baseWrapper);
        // 在线设备 (status=1)
        LambdaQueryWrapper<Device> onlineWrapper = buildDashboardDeviceWrapper();
        onlineWrapper.eq(Device::getStatus, 1);
        long onlineCount = deviceService.count(onlineWrapper);

        // 获取对应类型的设备全量数据用于计算
        java.util.List<Device> deviceList = deviceService.list(baseWrapper);

        // 核心：计算真实平均负载 (仅限在线节点)
        long onlineNodes = deviceList.stream().filter(d -> d.getStatus() != null && d.getStatus() == 1).count();
        double avgCpu = 0;
        double avgMem = 0;
        double avgGpu = 0;
        double avgGpuTemp = 0;
        double totalPower = 0;
        if (onlineNodes > 0) {
            avgCpu = deviceList.stream()
                    .filter(d -> d.getStatus() != null && d.getStatus() == 1 && d.getCpuUsage() != null)
                    .mapToDouble(d -> Double.parseDouble(d.getCpuUsage().replace("%", "")))
                    .average().orElse(0);
            avgMem = deviceList.stream()
                    .filter(d -> d.getStatus() != null && d.getStatus() == 1 && d.getMemoryUsage() != null)
                    .mapToDouble(d -> Double.parseDouble(d.getMemoryUsage().replace("%", "")))
                    .average().orElse(0);
            avgGpu = deviceList.stream()
                    .filter(d -> d.getStatus() != null && d.getStatus() == 1 && d.getGpuUsage() != null)
                    .mapToDouble(d -> parsePercentage(d.getGpuUsage()))
                    .average().orElse(0);
            avgGpuTemp = deviceList.stream()
                    .filter(d -> d.getStatus() != null && d.getStatus() == 1 && d.getGpuTemperature() != null)
                    .mapToDouble(Device::getGpuTemperature)
                    .average().orElse(0);
            totalPower = deviceList.stream()
                    .filter(d -> d.getStatus() != null && d.getStatus() == 1 && d.getPowerWatts() != null)
                    .mapToDouble(Device::getPowerWatts)
                    .sum();
        }

        stats.put("totalCount", totalCount);
        stats.put("onlineCount", onlineCount);
        stats.put("offlineCount", totalCount - onlineCount);
        stats.put("avgCpuLoad", Math.round(avgCpu));
        stats.put("avgMemLoad", Math.round(avgMem));
        stats.put("avgGpuLoad", Math.round(avgGpu));
        stats.put("avgGpuTemperature", Math.round(avgGpuTemp * 10.0) / 10.0);
        stats.put("totalPowerWatts", Math.round(totalPower * 10.0) / 10.0);

        return Result.success(stats);
    }

    private double parsePercentage(String value) {
        try {
            return Double.parseDouble(value.replace("%", "").trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * 分页查询设备列表（排除虚拟设备 type=1）
     */
    @GetMapping("/list")
    public Result<IPage<Device>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String sn,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Boolean remoteCapable) {

        Page<Device> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        // 新后台设备识别：具备边缘算力能力的真实设备（排除虚拟设备）
        applyDashboardDeviceFilter(wrapper);

        if (sn != null && !sn.isEmpty()) {
            wrapper.like(Device::getSn, sn);
        }
        if (status != null) {
            wrapper.eq(Device::getStatus, status);
        }
        if (location != null && !location.isEmpty()) {
            wrapper.like(Device::getLocation, location);
        }
        if (Boolean.TRUE.equals(remoteCapable)) {
            wrapper.eq(Device::getStatus, 1);
        }

        wrapper.orderByDesc(Device::getLastHeartbeatTime);

        IPage<Device> result = deviceService.page(pageObj, wrapper);
        fillDeviceRuntimeStats(result.getRecords());
        fillDeviceEnvStats(result.getRecords());
        return Result.success(result);
    }

    /**
     * 获取所有设备的不重复地区列表（用于前端筛选下拉框）
     */
    @GetMapping("/locations")
    public Result<Object> getLocations() {
        LambdaQueryWrapper<Device> wrapper = buildDashboardDeviceWrapper();
        wrapper.isNotNull(Device::getLocation);
        wrapper.ne(Device::getLocation, "");
        wrapper.select(Device::getLocation);

        List<Device> devices = deviceService.list(wrapper);

        // 提取省份并去重（从 "山东省枣庄市" 中提取 "山东省"）
        Map<String, List<String>> provinceMap = new java.util.LinkedHashMap<>();
        for (Device d : devices) {
            String loc = d.getLocation();
            if (loc == null || loc.isEmpty()) continue;

            String province;
            String city = loc;
            // 提取省份
            int idx = loc.indexOf("省");
            if (idx > 0) {
                province = loc.substring(0, idx + 1);
                city = loc.substring(idx + 1);
            } else {
                idx = loc.indexOf("市");
                if (idx > 0 && (loc.startsWith("北京") || loc.startsWith("上海") || loc.startsWith("天津") || loc.startsWith("重庆"))) {
                    province = loc.substring(0, idx + 1);
                    city = "";
                } else {
                    // 自治区等
                    province = loc;
                    city = "";
                }
            }

            provinceMap.computeIfAbsent(province, k -> new java.util.ArrayList<>());
            if (!city.isEmpty() && !provinceMap.get(province).contains(city)) {
                provinceMap.get(province).add(city);
            }
        }

        // 构造返回：去重的完整 location 列表 + 省份分组
        List<String> allLocations = devices.stream()
                .map(Device::getLocation)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<String> provinces = new java.util.ArrayList<>(provinceMap.keySet());
        java.util.Collections.sort(provinces);

        Map<String, Object> result = new HashMap<>();
        result.put("locations", allLocations);
        result.put("provinces", provinces);
        result.put("provinceMap", provinceMap);

        return Result.success(result);
    }

    private LambdaQueryWrapper<Device> buildDashboardDeviceWrapper() {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        applyDashboardDeviceFilter(wrapper);
        return wrapper;
    }

    private void applyDashboardDeviceFilter(LambdaQueryWrapper<Device> wrapper) {
        // 新后台只展示真正接入边缘链路的设备：type=2
        wrapper.eq(Device::getType, 2);
    }

    private void fillDeviceRuntimeStats(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }

        Set<String> sns = devices.stream()
                .map(Device::getSn)
                .filter(sn -> sn != null && !sn.isEmpty())
                .collect(Collectors.toSet());
        if (sns.isEmpty()) {
            return;
        }

        List<ComputeJob> tasks = computeJobService.lambdaQuery()
                .in(ComputeJob::getDeviceSn, sns)
                .orderByDesc(ComputeJob::getUpdateTime)
                .orderByDesc(ComputeJob::getCreateTime)
                .list();

        Map<String, String> runtimeModelMap = new HashMap<>();
        for (ComputeJob task : tasks) {
            if (task.getDeviceSn() == null || task.getDeviceSn().isEmpty()) {
                continue;
            }
            if (runtimeModelMap.containsKey(task.getDeviceSn())) {
                continue;
            }
            if (task.getModelName() != null && !task.getModelName().isEmpty()) {
                runtimeModelMap.put(task.getDeviceSn(), task.getModelName());
            }
        }

        for (Device device : devices) {
            device.setRuntimeModel(runtimeModelMap.getOrDefault(device.getSn(), "未执行任务"));
        }
    }

    private void fillDeviceEnvStats(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }

        Set<String> sns = devices.stream()
                .map(Device::getSn)
                .filter(sn -> sn != null && !sn.isEmpty())
                .collect(Collectors.toSet());
        if (sns.isEmpty()) {
            return;
        }

        List<DeviceCommand> checks = deviceCommandService.lambdaQuery()
                .in(DeviceCommand::getDeviceSn, sns)
                .eq(DeviceCommand::getCommandType, "ENV_CHECK")
                .orderByDesc(DeviceCommand::getUpdateTime)
                .orderByDesc(DeviceCommand::getCreateTime)
                .list();

        Map<String, DeviceCommand> latestMap = new HashMap<>();
        for (DeviceCommand check : checks) {
            if (check.getDeviceSn() != null && !latestMap.containsKey(check.getDeviceSn())) {
                latestMap.put(check.getDeviceSn(), check);
            }
        }

        for (Device device : devices) {
            DeviceCommand check = latestMap.get(device.getSn());
            applyEnvStatus(device, check);
        }
    }

    private void applyEnvStatus(Device device, DeviceCommand check) {
        if (check == null) {
            device.setEnvStatus("unknown");
            device.setEnvSummary("未检查");
            return;
        }

        device.setEnvCheckedAt(check.getFinishedAt() != null ? check.getFinishedAt() : check.getUpdateTime());
        if ("pending".equals(check.getStatus()) || "delivered".equals(check.getStatus())) {
            device.setEnvStatus("checking");
            device.setEnvSummary("检查中");
            return;
        }
        if ("failed".equals(check.getStatus())) {
            device.setEnvStatus("error");
            device.setEnvSummary("检查失败");
            return;
        }

        String result = check.getResultText() == null ? "" : check.getResultText();
        List<String> missing = result.lines()
                .filter(line -> line.startsWith("MISSING:"))
                .map(line -> line.substring("MISSING:".length()).trim())
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            device.setEnvStatus("ready");
            device.setEnvSummary("环境正常");
        } else {
            device.setEnvStatus("warning");
            device.setEnvSummary("缺少 " + missing.size() + " 项");
            device.setEnvMissingItems(String.join(", ", missing));
        }
    }

    /**
     * 更新设备备注/名称
     */
    @PostMapping("/update-name")
    public Result<Object> updateName(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        String name = (String) params.get("name");

        Device device = deviceService.getById(id);
        if (device == null)
            return Result.error("设备不存在");

        device.setName(name);
        deviceService.updateById(device);
        return Result.success("修改成功");
    }
}
