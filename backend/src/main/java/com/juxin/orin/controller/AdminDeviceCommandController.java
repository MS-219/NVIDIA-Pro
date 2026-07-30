package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.dto.DeviceCommandGroup;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.mapper.DeviceCommandMapper;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/device-commands")
public class AdminDeviceCommandController {

    private static final int MAX_BATCH_DEVICES = 2000;

    @Autowired
    private IDeviceCommandService deviceCommandService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private DeviceCommandMapper deviceCommandMapper;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(deviceCommandService.getStats());
    }

    @GetMapping("/groups")
    public Result<IPage<DeviceCommandGroup>> groups(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String deviceSn,
            @RequestParam(required = false) String commandType,
            @RequestParam(required = false) String status) {
        String normalizedDeviceSn = normalize(deviceSn);
        String normalizedCommandType = normalize(commandType);
        String normalizedStatus = normalize(status);

        // Complex GROUP BY queries are counted explicitly. Disabling the automatic
        // MyBatis-Plus count avoids database-specific failures in generated count SQL.
        Page<DeviceCommandGroup> pageRequest = new Page<>(page, size, false);
        IPage<DeviceCommandGroup> result = deviceCommandMapper.selectGroupPage(
                pageRequest,
                normalizedDeviceSn,
                normalizedCommandType,
                normalizedStatus);
        Long total = deviceCommandMapper.selectGroupCount(
                normalizedDeviceSn,
                normalizedCommandType,
                normalizedStatus);
        result.setTotal(total == null ? 0L : total);
        return Result.success(result);
    }

    @GetMapping("/group-records")
    public Result<IPage<DeviceCommand>> groupRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam String groupKey,
            @RequestParam(required = false) String deviceSn,
            @RequestParam(required = false) String status) {
        if (groupKey == null || groupKey.trim().isEmpty()) {
            return Result.error("任务标识不能为空");
        }
        IPage<DeviceCommand> result = deviceCommandMapper.selectGroupRecords(
                new Page<>(page, size),
                groupKey.trim(),
                normalize(deviceSn),
                normalize(status));
        return Result.success(result);
    }

    @GetMapping("/list")
    public Result<IPage<DeviceCommand>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String deviceSn,
            @RequestParam(required = false) String commandType,
            @RequestParam(required = false) String status) {

        LambdaQueryWrapper<DeviceCommand> wrapper = new LambdaQueryWrapper<>();
        if (deviceSn != null && !deviceSn.trim().isEmpty()) {
            wrapper.like(DeviceCommand::getDeviceSn, deviceSn.trim());
        }
        if (commandType != null && !commandType.trim().isEmpty()) {
            wrapper.eq(DeviceCommand::getCommandType, commandType.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(DeviceCommand::getStatus, status.trim());
        }
        wrapper.orderByDesc(DeviceCommand::getCreateTime);
        return Result.success(deviceCommandService.page(new Page<>(page, size), wrapper));
    }

    @PostMapping("/dispatch")
    public Result<DeviceCommand> dispatch(@RequestBody Map<String, Object> params) {
        Long deviceId = getLong(params, "deviceId");
        String deviceSn = getString(params, "deviceSn");
        String commandType = getString(params, "commandType");
        String commandPayload = getString(params, "commandPayload");
        String remark = getString(params, "remark");

        if (commandType == null) {
            return Result.error("请选择指令类型");
        }

        Device device = resolveDevice(deviceId, deviceSn);
        if (device == null) {
            return Result.error("设备不存在");
        }
        if (!isRemoteCommandCapable(device)) {
            return Result.error("该设备未接入远程 Agent，不能下发远程指令");
        }

        String commandText = buildCommandText(commandType, params);
        if (commandText == null || commandText.trim().isEmpty()) {
            return Result.error("指令内容不能为空");
        }

        DeviceCommand command = deviceCommandService.dispatchCommand(
                device.getId(),
                device.getSn(),
                commandType,
                commandText,
                commandPayload,
                remark);
        return command == null ? Result.error("下发失败") : Result.success(command);
    }

    @GetMapping("/target-count")
    public Result<Map<String, Object>> targetCount(
            @RequestParam(required = false) String targetScope,
            @RequestParam(required = false) String locationKeyword,
            @RequestParam(required = false) String carrier) {
        Map<String, Object> result = new HashMap<>();
        result.put("count", countTargetDevices(targetScope, locationKeyword, carrier));
        return Result.success(result);
    }

    @PostMapping("/dispatch-batch")
    public Result<Map<String, Object>> dispatchBatch(@RequestBody Map<String, Object> params) {
        String commandType = getString(params, "commandType");
        String commandPayload = getString(params, "commandPayload");
        String remark = getString(params, "remark");
        String targetScope = getString(params, "targetScope");
        if (commandType == null) {
            return Result.error("请选择指令类型");
        }
        if (targetScope == null || "SINGLE".equals(targetScope)) {
            return Result.error("批量下发请选择目标范围");
        }

        String commandText = buildCommandText(commandType, params);
        if (commandText == null || commandText.trim().isEmpty()) {
            return Result.error("指令内容不能为空");
        }

        List<Device> devices = queryTargetDevices(
                targetScope,
                getString(params, "locationKeyword"),
                getString(params, "carrier"));
        if (devices.isEmpty()) {
            return Result.error("目标范围内没有在线设备");
        }
        if (devices.size() > MAX_BATCH_DEVICES) {
            return Result.error("一次最多下发 " + MAX_BATCH_DEVICES + " 台设备，请缩小范围");
        }

        List<DeviceCommand> commands = deviceCommandService.dispatchCommands(
                devices,
                commandType,
                commandText,
                commandPayload,
                remark);
        Map<String, Object> result = new HashMap<>();
        result.put("count", commands.size());
        result.put("targetScope", targetScope);
        return Result.success(result);
    }

    @PostMapping("/cancel")
    public Result<String> cancel(@RequestBody Map<String, Object> params) {
        Long id = getLong(params, "id");
        if (id == null) {
            return Result.error("id 不能为空");
        }
        return deviceCommandService.cancelCommand(id)
                ? Result.success("已取消")
                : Result.error("只能取消待下发指令");
    }

    @GetMapping("/templates")
    public Result<Map<String, String>> templates() {
        Map<String, String> templates = new HashMap<>();
        templates.put("HEALTH_CHECK", "健康检查");
        templates.put("ENV_CHECK", "远程环境检查");
        templates.put("UPGRADE_AGENT", "升级远程 Agent");
        templates.put("INSTALL_DEPS", "安装远程运维依赖");
        templates.put("OPEN_TUNNEL", "建立隧道");
        templates.put("CLOSE_TUNNEL", "关闭隧道");
        templates.put("RESTART_AGENT", "重启 Agent");
        templates.put("CUSTOM", "自定义命令");
        return Result.success(templates);
    }

    @PostMapping("/submit-result")
    public Result<String> submitResult(@RequestBody Map<String, Object> params) {
        String commandNo = getString(params, "commandNo");
        Integer exitCode = getInt(params, "exitCode");
        String resultText = getString(params, "resultText");
        if (commandNo == null) {
            return Result.error("commandNo 不能为空");
        }
        return deviceCommandService.submitResult(commandNo, exitCode, resultText)
                ? Result.success("已记录")
                : Result.error("指令不存在");
    }

    private Device resolveDevice(Long deviceId, String deviceSn) {
        if (deviceId != null) {
            return deviceService.getById(deviceId);
        }
        if (deviceSn != null && !deviceSn.trim().isEmpty()) {
            return deviceService.lambdaQuery().eq(Device::getSn, deviceSn.trim()).one();
        }
        return null;
    }

    private Long countTargetDevices(String targetScope, String locationKeyword, String carrier) {
        LambdaQueryWrapper<Device> wrapper = buildTargetWrapper(targetScope, locationKeyword, carrier);
        if (wrapper == null) {
            return 0L;
        }
        return deviceService.count(wrapper);
    }

    private List<Device> queryTargetDevices(String targetScope, String locationKeyword, String carrier) {
        LambdaQueryWrapper<Device> wrapper = buildTargetWrapper(targetScope, locationKeyword, carrier);
        if (wrapper == null) {
            return java.util.Collections.emptyList();
        }
        wrapper.orderByDesc(Device::getLastHeartbeatTime);
        return deviceService.list(wrapper);
    }

    private LambdaQueryWrapper<Device> buildTargetWrapper(String targetScope, String locationKeyword, String carrier) {
        if (targetScope == null || targetScope.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getStatus, 1)
                .eq(Device::getType, 2);
        if ("ALL_ONLINE".equals(targetScope)) {
            return wrapper;
        }
        if ("LOCATION".equals(targetScope)) {
            if (locationKeyword == null || locationKeyword.trim().isEmpty()) {
                return null;
            }
            return wrapper.like(Device::getLocation, locationKeyword.trim());
        }
        if ("CARRIER".equals(targetScope)) {
            if (carrier == null || carrier.trim().isEmpty()) {
                return null;
            }
            return wrapper.eq(Device::getCarrier, carrier.trim());
        }
        return null;
    }

    private boolean isRemoteCommandCapable(Device device) {
        return device != null
                && device.getStatus() != null
                && device.getStatus() == 1
                && device.getType() != null
                && device.getType() == 2;
    }

    private String buildCommandText(String commandType, Map<String, Object> params) {
        String custom = getString(params, "commandText");
        if ("CUSTOM".equals(commandType)) {
            return custom;
        }
        if ("HEALTH_CHECK".equals(commandType)) {
            return "echo ORIN_HEALTH_OK && date && uname -a";
        }
        if ("ENV_CHECK".equals(commandType)) {
            return "echo ORIN_ENV_CHECK; date; uname -a; cat /proc/device-tree/model 2>/dev/null || true; "
                    + "cat /etc/nv_tegra_release 2>/dev/null | head -1 || true; "
                    + "for cmd in python3 curl wget ssh nvidia-smi tegrastats docker; do if command -v $cmd >/dev/null 2>&1; then echo OK:$cmd=$(command -v $cmd); else echo MISSING:$cmd; fi; done; "
                    + "[ -d /opt/juxin-orin ] && echo OK:/opt/juxin-orin || echo MISSING:/opt/juxin-orin; "
                    + "systemctl status juxin-orin-agent.service --no-pager 2>/dev/null | head -20 || true";
        }
        if ("UPGRADE_AGENT".equals(commandType)) {
            String publicBase = System.getenv().getOrDefault(
                    "ORIN_PUBLIC_BASE_URL",
                    "https://nvidia.juxinsuanli.cn");
            return "set -e; BASE=" + publicBase + "/api/agent; WORK=/tmp/juxin-orin-agent-upgrade; mkdir -p $WORK /opt/juxin-orin/runtime; cd $WORK; "
                    + "if command -v curl >/dev/null 2>&1; then DL='curl -fsSLO'; elif command -v wget >/dev/null 2>&1; then DL='wget -q'; else echo MISSING:curl_or_wget; exit 127; fi; "
                    + "for f in orin_agent.py orin_display.py install-agent.sh juxin-orin-agent.service juxin-orin-display.service; do $DL $BASE/$f; done; "
                    + "chmod +x install-agent.sh orin_agent.py orin_display.py; "
                    + "nohup env ORIN_REMOTE_UPGRADE=1 bash ./install-agent.sh >/opt/juxin-orin/runtime/upgrade.log 2>&1 & "
                    + "echo ORIN_AGENT_UPGRADE_STARTED; echo LOG:/opt/juxin-orin/runtime/upgrade.log";
        }
        if ("INSTALL_DEPS".equals(commandType)) {
            return "set -e; "
                    + "if command -v apt-get >/dev/null 2>&1; then apt-get update && apt-get install -y curl wget openssh-client python3; "
                    + "elif command -v yum >/dev/null 2>&1; then yum install -y curl wget openssh-clients python3; "
                    + "elif command -v dnf >/dev/null 2>&1; then dnf install -y curl wget openssh-clients python3; "
                    + "else echo unsupported package manager; fi; "
                    + "mkdir -p /opt/juxin-orin/runtime; echo ORIN_REMOTE_DEPS_READY; "
                    + "for cmd in python3 curl wget ssh; do command -v $cmd >/dev/null 2>&1 && echo OK:$cmd || echo MISSING:$cmd; done";
        }
        if ("OPEN_TUNNEL".equals(commandType)) {
            return custom != null ? custom : "/opt/juxin-orin/tunnel-control.sh restart";
        }
        if ("CLOSE_TUNNEL".equals(commandType)) {
            return "/opt/juxin-orin/tunnel-control.sh stop";
        }
        if ("RESTART_AGENT".equals(commandType)) {
            return "systemctl restart juxin-orin-agent.service || service juxin-orin-agent restart";
        }
        return custom;
    }

    private String getString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        return value.toString().trim();
    }

    private Long getLong(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    private Integer getInt(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        return Integer.valueOf(value.toString());
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
