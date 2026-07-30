package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.juxin.orin.common.Result;
import com.juxin.orin.dto.EdgeEnrollRequest;
import com.juxin.orin.dto.EdgeEnrollResponse;
import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.exception.EdgeDeviceApiException;
import com.juxin.orin.service.IComputeJobService;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IEdgeDeviceAccessService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IDeviceUpgradeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 边缘端节点专用控制器 (Edge Node API)
 */
@RestController
@RequestMapping("/api/edge")
public class EdgeDeviceController {

    private static final String EDGE_PROTOCOL_VERSION = "2";
    private static final String MINIMUM_AGENT_VERSION = "0.5.0-orin";

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IEdgeDeviceAccessService edgeDeviceAccessService;

    @Autowired
    private IComputeJobService computeJobService;

    @Autowired
    private IDeviceCommandService deviceCommandService;

    @Autowired
    private IDeviceUpgradeService deviceUpgradeService;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    /**
     * Read-only deployment contract used by the manufacturing preflight gate.
     */
    @GetMapping("/capabilities")
    public Result<Map<String, Object>> capabilities() {
        Map<String, Object> response = new HashMap<>();
        response.put("protocolVersion", EDGE_PROTOCOL_VERSION);
        response.put("minimumAgentVersion", MINIMUM_AGENT_VERSION);
        response.put("directEnrollment", true);
        response.put("imageLicenseRequired", false);
        response.put("deviceTokenAuthentication", true);
        response.put("fullscreenStatusDisplay", true);
        response.put("atomicTaskClaim", true);
        response.put("persistentResultOutbox", true);
        response.put("taskHandlers", List.of("ollama", "external-runner"));
        return Result.success(response);
    }

    /**
     * 使用设备 SN 和硬件指纹完成一次性入网，并换取每台设备独立的访问令牌。
     * 原始令牌只在这个响应中返回一次，服务端仅保存 SHA-256。
     */
    @PostMapping("/enroll")
    public Result<EdgeEnrollResponse> enroll(
            @RequestBody EdgeEnrollRequest enrollRequest,
            HttpServletRequest request) {
        String reportedIp = enrollRequest != null && enrollRequest.telemetry() != null
                && enrollRequest.telemetry().get("ip") != null
                        ? enrollRequest.telemetry().get("ip").toString()
                        : null;
        return Result.success(edgeDeviceAccessService.enroll(
                enrollRequest,
                resolveHeartbeatIp(request, reportedIp)));
    }

    /**
     * 1. 节点心跳与状态上报
     * Agent 按配置间隔调用（默认 60s）
     */
    @PostMapping("/report")
    public Result<Map<String, Object>> reportStatus(@RequestBody Map<String, Object> report,
            HttpServletRequest request,
            @RequestHeader(value = IEdgeDeviceAccessService.DEVICE_TOKEN_HEADER, required = false) String deviceToken) {
        Device authenticatedDevice = edgeDeviceAccessService.authenticate(deviceToken);
        if (report == null) {
            throw new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, "report is required");
        }
        String sn = getReportText(report, "sn");
        if (sn == null) {
            throw new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, "sn is required");
        }
        edgeDeviceAccessService.requireOwnedSn(authenticatedDevice, sn);
        edgeDeviceAccessService.requireMatchingHardwareFingerprint(
                authenticatedDevice,
                getReportText(report, "hardware_fingerprint"));
        String reportedIp = report.get("ip") != null ? report.get("ip").toString() : null;
        String ip = resolveHeartbeatIp(request, reportedIp);
        String cpuUsage = report.get("cpu_load") != null ? report.get("cpu_load").toString() : "0";
        String memUsage = report.get("mem_load") != null ? report.get("mem_load").toString() : "0";
        String cpuModel = report.get("cpu_model") != null ? report.get("cpu_model").toString() : null;
        String agentVersion = report.get("agent_version") != null ? report.get("agent_version").toString() : null;
        String imageVersion = getReportText(report, "image_version");

        // 更新设备心跳
        Device device;
        try {
            device = deviceService.handleHeartbeat(
                    sn,
                    ip,
                    cpuUsage,
                    memUsage);
        } catch (IllegalArgumentException e) {
            throw new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, e.getMessage());
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
            if (imageVersion != null && !imageVersion.equals(device.getImageVersion())) {
                device.setImageVersion(imageVersion);
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
    public Result<ComputeJob> fetchTask(
            @RequestParam String sn,
            @RequestHeader(value = IEdgeDeviceAccessService.DEVICE_TOKEN_HEADER, required = false) String deviceToken) {
        Device authenticatedDevice = edgeDeviceAccessService.authenticate(deviceToken);
        edgeDeviceAccessService.requireOwnedSn(authenticatedDevice, sn);
        String normalizedSn = sn.trim();

        ComputeJob task = computeJobService.claimNextPendingTask(normalizedSn);
        if (task != null) {
            return Result.success(task);
        }

        return Result.success(null); // 无任务
    }

    /**
     * 3. 节点回传任务执行结果 (Submit Result)
     */
    @PostMapping("/tasks/submit")
    public Result<String> submitResult(
            @RequestBody ComputeJob result,
            @RequestHeader(value = IEdgeDeviceAccessService.DEVICE_TOKEN_HEADER, required = false) String deviceToken) {
        Device authenticatedDevice = edgeDeviceAccessService.authenticate(deviceToken);
        if (result == null || result.getId() == null) {
            throw new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, "任务 ID 不能为空");
        }
        edgeDeviceAccessService.requireOwnedSn(authenticatedDevice, result.getDeviceSn());

        ComputeJob task = computeJobService.getById(result.getId());
        if (task == null) {
            throw new EdgeDeviceApiException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        if (!authenticatedDevice.getSn().equals(task.getDeviceSn())) {
            throw new EdgeDeviceApiException(HttpStatus.FORBIDDEN, "任务不属于当前设备");
        }
        if ("completed".equals(task.getStatus()) || "failed".equals(task.getStatus())) {
            if (task.getStatus().equals(result.getStatus())) {
                return Result.success("Contribution already recorded");
            }
            throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "任务结果与已记录状态不一致");
        }
        if (!"running".equals(task.getStatus())) {
            throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "任务当前状态不允许提交");
        }
        if (!"completed".equals(result.getStatus()) && !"failed".equals(result.getStatus())) {
            throw new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, "任务结果状态必须是 completed 或 failed");
        }

        int reward = "completed".equals(result.getStatus())
                ? Math.max(1, (result.getGenerateTokens() == null ? 0 : result.getGenerateTokens()) / 100)
                : 0;
        boolean updated = computeJobService.update(new UpdateWrapper<ComputeJob>()
                .eq("id", task.getId())
                .eq("status", "running")
                .eq("device_sn", authenticatedDevice.getSn())
                .eq("deleted", 0)
                .set("status", result.getStatus())
                .set("response_text", result.getResponseText())
                .set("generate_tokens", result.getGenerateTokens())
                .set("duration_ms", result.getDurationMs())
                .set("error_msg", result.getErrorMsg())
                .set("reward_hashrate", reward)
                .set("update_time", LocalDateTime.now()));
        if (!updated) {
            ComputeJob current = computeJobService.getById(result.getId());
            if (current != null && authenticatedDevice.getSn().equals(current.getDeviceSn())
                    && result.getStatus().equals(current.getStatus())) {
                return Result.success("Contribution already recorded");
            }
            throw new EdgeDeviceApiException(HttpStatus.CONFLICT, "任务已被其他状态变更接管");
        }
        return Result.success("Contribution recorded");
    }

    /**
     * 4. 节点回传远程指令执行结果
     */
    @PostMapping("/commands/submit")
    public Result<String> submitCommandResult(
            @RequestBody Map<String, Object> result,
            @RequestHeader(value = IEdgeDeviceAccessService.DEVICE_TOKEN_HEADER, required = false) String deviceToken) {
        Device authenticatedDevice = edgeDeviceAccessService.authenticate(deviceToken);
        if (result == null) {
            throw new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, "指令结果不能为空");
        }
        String sn = getReportText(result, "sn");
        edgeDeviceAccessService.requireOwnedSn(authenticatedDevice, sn);

        String commandNo = result.get("commandNo") != null ? result.get("commandNo").toString() : null;
        if (commandNo == null || commandNo.isBlank()) {
            throw new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, "commandNo is required");
        }
        DeviceCommand command = deviceCommandService.getOne(new LambdaQueryWrapper<DeviceCommand>()
                .eq(DeviceCommand::getCommandNo, commandNo.trim())
                .last("LIMIT 1"));
        if (command == null) {
            throw new EdgeDeviceApiException(HttpStatus.NOT_FOUND, "指令不存在");
        }
        if (!authenticatedDevice.getSn().equals(command.getDeviceSn())) {
            throw new EdgeDeviceApiException(HttpStatus.FORBIDDEN, "指令不属于当前设备");
        }

        Integer exitCode = null;
        if (result.get("exitCode") != null) {
            try {
                exitCode = Integer.valueOf(result.get("exitCode").toString());
            } catch (NumberFormatException exception) {
                throw new EdgeDeviceApiException(HttpStatus.BAD_REQUEST, "exitCode 格式错误");
            }
        }
        String resultText = result.get("resultText") != null ? result.get("resultText").toString() : null;
        boolean success = deviceCommandService.submitResult(commandNo, exitCode, resultText);
        return success ? Result.success("Command result recorded") : Result.error("指令不存在");
    }
}
