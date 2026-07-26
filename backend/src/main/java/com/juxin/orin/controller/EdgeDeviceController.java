package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AiDeviceTask;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.service.IAiDeviceTaskService;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IDeviceUpgradeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 边缘端节点专用控制器 (Edge Node API)
 */
@RestController
@RequestMapping("/api/edge")
public class EdgeDeviceController {

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IAiDeviceTaskService aiDeviceTaskService;

    @Autowired
    private IDeviceCommandService deviceCommandService;

    @Autowired
    private IDeviceUpgradeService deviceUpgradeService;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    /**
     * 1. 节点心跳与状态上报
     * Agent 按配置间隔调用（默认 60s）
     */
    @PostMapping("/report")
    public Result<Map<String, Object>> reportStatus(@RequestBody Map<String, Object> report,
            HttpServletRequest request) {
        String sn = (String) report.get("sn");
        if (sn == null || sn.trim().isEmpty()) {
            return Result.error("sn is required");
        }
        sn = sn.trim();
        String reportedIp = report.get("ip") != null ? report.get("ip").toString() : null;
        String ip = resolveHeartbeatIp(request, reportedIp);
        String cpuUsage = report.get("cpu_load") != null ? report.get("cpu_load").toString() : "0";
        String memUsage = report.get("mem_load") != null ? report.get("mem_load").toString() : "0";
        String cpuModel = report.get("cpu_model") != null ? report.get("cpu_model").toString() : null;
        String agentVersion = report.get("agent_version") != null ? report.get("agent_version").toString() : null;
        String imageLicenseKey = getReportText(report, "image_license");
        String imageVersion = getReportText(report, "image_version");
        String hardwareFingerprint = getReportText(report, "hardware_fingerprint");

        // 更新设备心跳
        Device device;
        try {
            device = deviceService.handleHeartbeat(
                    sn,
                    ip,
                    cpuUsage,
                    memUsage,
                    imageLicenseKey,
                    imageVersion,
                    hardwareFingerprint,
                    cpuModel,
                    agentVersion);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        if (device != null) {
            boolean shouldUpdate = false;
            if (device.getType() == null || device.getType() != 2) {
                device.setType(2); // 2 代表 Python Agent 设备 (AI推理/营销节点)
                shouldUpdate = true;
            }
            if (cpuModel != null && !cpuModel.isEmpty() && !cpuModel.equals(device.getCpuModel())) {
                device.setCpuModel(cpuModel);
                shouldUpdate = true;
            }
            if (agentVersion != null && !agentVersion.isEmpty() && !agentVersion.equals(device.getAgentVersion())) {
                device.setAgentVersion(agentVersion);
                shouldUpdate = true;
            }
            shouldUpdate |= applyOrinTelemetry(device, report);
            if (shouldUpdate) {
                deviceService.updateById(device);
            }
            if (agentVersion != null && !agentVersion.isEmpty()) {
                deviceUpgradeService.markSuccessByHeartbeat(device.getSn(), agentVersion);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("action", "none");

        // 下发心跳间隔配置（秒），Agent 收到后动态调整
        int heartbeatInterval = Integer.parseInt(
                configService.getConfig("device.heartbeatInterval", "60"));
        response.put("heartbeatInterval", heartbeatInterval);

        // 下发任务轮询间隔配置（秒）
        int taskPollInterval = Integer.parseInt(
                configService.getConfig("device.taskPollInterval", "60"));
        response.put("taskPollInterval", taskPollInterval);

        if (device != null) {
            DeviceCommand command = deviceCommandService.takePendingCommand(device.getSn());
            if (command != null) {
                response.put("action", "execute_command");
                response.put("commandNo", command.getCommandNo());
                response.put("commandType", command.getCommandType());
                response.put("command", command.getCommandText());

                // 清除兼容字段，避免同一条命令重复执行。
                device.setPendingCommand("");
                deviceService.updateById(device);
            } else if (device.getPendingCommand() != null && !device.getPendingCommand().isEmpty()) {
                // 兼容旧后台直接写入 pending_command 的临时指令。
                response.put("action", "execute_command");
                response.put("command", device.getPendingCommand());

                device.setPendingCommand("");
                deviceService.updateById(device);
            }
        }

        return Result.success(response);
    }

    private boolean applyOrinTelemetry(Device device, Map<String, Object> report) {
        boolean changed = false;
        changed |= setTextIfPresent(report, "device_model", device.getDeviceModel(), device::setDeviceModel);
        changed |= setTextIfPresent(report, "architecture", device.getArchitecture(), device::setArchitecture);
        changed |= setTextIfPresent(report, "l4t_version", device.getL4tVersion(), device::setL4tVersion);
        changed |= setTextIfPresent(report, "cuda_version", device.getCudaVersion(), device::setCudaVersion);
        changed |= setTextIfPresent(report, "gpu_usage", device.getGpuUsage(), device::setGpuUsage);

        Double temperature = getDouble(report, "gpu_temperature");
        if (temperature != null) {
            device.setGpuTemperature(temperature);
            changed = true;
        }
        Double power = getDouble(report, "power_watts");
        if (power != null) {
            device.setPowerWatts(power);
            changed = true;
        }
        Integer memory = getInteger(report, "memory_total_mb");
        if (memory != null) {
            device.setMemoryTotalMb(memory);
            changed = true;
        }
        return changed;
    }

    private boolean setTextIfPresent(
            Map<String, Object> report,
            String key,
            String current,
            java.util.function.Consumer<String> setter) {
        String value = getReportText(report, key);
        if (value == null || value.equals(current)) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private Double getDouble(Map<String, Object> report, String key) {
        try {
            Object value = report.get(key);
            return value == null ? null : Double.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer getInteger(Map<String, Object> report, String key) {
        try {
            Object value = report.get(key);
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String getReportText(Map<String, Object> report, String key) {
        Object value = report.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String resolveHeartbeatIp(HttpServletRequest request, String reportedIp) {
        String clientIp = getClientIp(request);
        if (isPublicIp(clientIp)) {
            return clientIp;
        }
        if (reportedIp != null && !reportedIp.isBlank()) {
            return reportedIp.trim();
        }
        return clientIp;
    }

    private String getClientIp(HttpServletRequest request) {
        // 优先使用 CDN 或代理写入的可靠 Header
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip != null && ip.contains(",")) {
                // 如果有多个代理，取最左边第一个（或者是客户端伪造的，这取决于Nginx配置）
                ip = ip.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private boolean isPublicIp(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip.trim());
            return !(address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isMulticastAddress());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 2. 节点领取挂起任务 (Fetch Task)
     */
    @GetMapping("/tasks/fetch")
    public Result<AiDeviceTask> fetchTask(@RequestParam String sn) {
        // 查找属于该节点或公共池中处于 pending 状态的最早任务
        LambdaQueryWrapper<AiDeviceTask> query = new LambdaQueryWrapper<>();
        query.eq(AiDeviceTask::getStatus, "pending")
                .and(i -> i.eq(AiDeviceTask::getDeviceSn, sn).or().isNull(AiDeviceTask::getDeviceSn))
                .orderByAsc(AiDeviceTask::getCreateTime)
                .last("LIMIT 1");

        AiDeviceTask task = aiDeviceTaskService.getOne(query);

        if (task != null) {
            // 锁定任务为 running 状态并分配给该 SN
            task.setStatus("running");
            task.setDeviceSn(sn);
            task.setUpdateTime(LocalDateTime.now());
            aiDeviceTaskService.updateById(task);
            return Result.success(task);
        }

        return Result.success(null); // 无任务
    }

    /**
     * 3. 节点回传任务执行结果 (Submit Result)
     */
    @PostMapping("/tasks/submit")
    public Result<String> submitResult(@RequestBody AiDeviceTask result) {
        AiDeviceTask task = aiDeviceTaskService.getById(result.getId());
        if (task == null) {
            return Result.error("任务不存在");
        }

        // 更新执行详情
        task.setStatus(result.getStatus()); // completed 或 failed
        task.setResponseText(result.getResponseText());
        task.setGenerateTokens(result.getGenerateTokens());
        task.setDurationMs(result.getDurationMs());
        task.setErrorMsg(result.getErrorMsg());
        task.setUpdateTime(LocalDateTime.now());

        // 计算奖励算力 (简易逻辑：100 Tokens = 1 绩效)
        if ("completed".equals(task.getStatus())) {
            int reward = (task.getGenerateTokens() != null ? task.getGenerateTokens() : 0) / 100;
            task.setRewardHashrate(Math.max(1, reward));
        }

        aiDeviceTaskService.updateById(task);
        return Result.success("Contribution recorded");
    }

    /**
     * 4. 节点回传远程指令执行结果
     */
    @PostMapping("/commands/submit")
    public Result<String> submitCommandResult(@RequestBody Map<String, Object> result) {
        String commandNo = result.get("commandNo") != null ? result.get("commandNo").toString() : null;
        Integer exitCode = null;
        if (result.get("exitCode") != null) {
            exitCode = Integer.valueOf(result.get("exitCode").toString());
        }
        String resultText = result.get("resultText") != null ? result.get("resultText").toString() : null;
        boolean success = deviceCommandService.submitResult(commandNo, exitCode, resultText);
        return success ? Result.success("Command result recorded") : Result.error("指令不存在");
    }
}
