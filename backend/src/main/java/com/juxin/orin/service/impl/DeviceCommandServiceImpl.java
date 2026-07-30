package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.mapper.DeviceCommandMapper;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IDeviceUpgradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DeviceCommandServiceImpl extends ServiceImpl<DeviceCommandMapper, DeviceCommand> implements IDeviceCommandService {

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    @Lazy
    private IDeviceUpgradeService deviceUpgradeService;

    @Override
    @Transactional
    public DeviceCommand dispatchCommand(Long deviceId, String deviceSn, String commandType,
                                         String commandText, String commandPayload, String remark) {
        Device device = resolveDevice(deviceId, deviceSn);
        if (device == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        DeviceCommand command = new DeviceCommand();
        command.setCommandNo("CMD" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        command.setDeviceId(device.getId());
        command.setDeviceSn(device.getSn());
        command.setCommandType(commandType);
        command.setCommandText(commandText);
        command.setCommandPayload(commandPayload);
        command.setStatus("pending");
        command.setRemark(remark);
        command.setCreateTime(now);
        command.setUpdateTime(now);
        save(command);

        // 兼容现有 Agent：老链路仍从 device.pending_command 读取。
        deviceService.lambdaUpdate()
                .eq(Device::getId, device.getId())
                .set(Device::getPendingCommand, commandText)
                .update();
        return command;
    }

    @Override
    @Transactional
    public List<DeviceCommand> dispatchCommands(List<Device> devices, String commandType,
                                                String commandText, String commandPayload, String remark) {
        List<DeviceCommand> commands = new ArrayList<>();
        if (devices == null || devices.isEmpty()) {
            return commands;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Device device : devices) {
            if (device == null || device.getId() == null || device.getSn() == null || device.getSn().trim().isEmpty()) {
                continue;
            }
            DeviceCommand command = new DeviceCommand();
            command.setCommandNo("CMD" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            command.setDeviceId(device.getId());
            command.setDeviceSn(device.getSn());
            command.setCommandType(commandType);
            command.setCommandText(commandText);
            command.setCommandPayload(commandPayload);
            command.setStatus("pending");
            command.setRemark(remark);
            command.setCreateTime(now);
            command.setUpdateTime(now);
            commands.add(command);
        }

        if (commands.isEmpty()) {
            return commands;
        }
        saveBatch(commands);
        for (DeviceCommand command : commands) {
            deviceService.lambdaUpdate()
                    .eq(Device::getId, command.getDeviceId())
                    .set(Device::getPendingCommand, commandText)
                    .update();
        }
        return commands;
    }

    @Override
    @Transactional
    public DeviceCommand takePendingCommand(String deviceSn) {
        if (deviceSn == null || deviceSn.trim().isEmpty()) {
            return null;
        }
        String normalizedSn = deviceSn.trim();
        DeviceCommand delivered = baseMapper.selectOne(new QueryWrapper<DeviceCommand>()
                .eq("device_sn", normalizedSn)
                .eq("status", "delivered")
                .eq("deleted", 0)
                .orderByAsc("dispatched_at")
                .orderByAsc("id")
                .last("LIMIT 1"));
        if (delivered != null) {
            return delivered;
        }

        DeviceCommand command = baseMapper.selectOne(new QueryWrapper<DeviceCommand>()
                .eq("device_sn", normalizedSn)
                .eq("status", "pending")
                .eq("deleted", 0)
                .orderByAsc("create_time")
                .orderByAsc("id")
                .last("LIMIT 1"));
        if (command == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = baseMapper.update(null, new UpdateWrapper<DeviceCommand>()
                .eq("id", command.getId())
                .eq("status", "pending")
                .eq("deleted", 0)
                .set("status", "delivered")
                .set("dispatched_at", now)
                .set("update_time", now));
        if (updated != 1) {
            return null;
        }
        command.setStatus("delivered");
        command.setDispatchedAt(now);
        command.setUpdateTime(now);
        if ("UPGRADE_AGENT".equals(command.getCommandType())) {
            deviceUpgradeService.markCommandDelivered(command.getCommandNo());
        }
        return command;
    }

    @Override
    @Transactional
    public boolean cancelCommand(Long id) {
        if (id == null) {
            return false;
        }
        DeviceCommand command = getById(id);
        if (command == null || !Objects.equals(command.getStatus(), "pending")) {
            return false;
        }
        boolean canceled = lambdaUpdate()
                .eq(DeviceCommand::getId, id)
                .eq(DeviceCommand::getStatus, "pending")
                .set(DeviceCommand::getStatus, "canceled")
                .set(DeviceCommand::getUpdateTime, LocalDateTime.now())
                .update();
        if (canceled && command.getDeviceId() != null) {
            Device device = deviceService.getById(command.getDeviceId());
            if (device != null && Objects.equals(device.getPendingCommand(), command.getCommandText())) {
                deviceService.lambdaUpdate()
                        .eq(Device::getId, command.getDeviceId())
                        .set(Device::getPendingCommand, null)
                        .update();
            }
        }
        return canceled;
    }

    @Override
    @Transactional
    public boolean submitResult(String commandNo, Integer exitCode, String resultText) {
        if (commandNo == null || commandNo.trim().isEmpty()) {
            return false;
        }
        DeviceCommand command = baseMapper.selectOne(new QueryWrapper<DeviceCommand>()
                .eq("command_no", commandNo.trim())
                .eq("deleted", 0)
                .last("LIMIT 1"));
        if (command == null) {
            return false;
        }
        String status = exitCode != null && exitCode == 0 ? "completed" : "failed";
        if ("completed".equals(command.getStatus()) || "failed".equals(command.getStatus())) {
            return status.equals(command.getStatus());
        }
        if (!"delivered".equals(command.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = baseMapper.update(null, new UpdateWrapper<DeviceCommand>()
                .eq("id", command.getId())
                .eq("status", "delivered")
                .eq("deleted", 0)
                .set("status", status)
                .set("exit_code", exitCode)
                .set("result_text", trimResult(resultText))
                .set("finished_at", now)
                .set("update_time", now));
        if (updated == 1 && "UPGRADE_AGENT".equals(command.getCommandType())) {
            deviceUpgradeService.handleCommandResult(command.getCommandNo(), exitCode, resultText);
        }
        if (updated == 1) {
            return true;
        }
        DeviceCommand current = baseMapper.selectById(command.getId());
        return current != null && status.equals(current.getStatus());
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", lambdaQuery().eq(DeviceCommand::getStatus, "pending").count());
        stats.put("delivered", lambdaQuery().eq(DeviceCommand::getStatus, "delivered").count());
        stats.put("completed", lambdaQuery().eq(DeviceCommand::getStatus, "completed").count());
        stats.put("canceled", lambdaQuery().eq(DeviceCommand::getStatus, "canceled").count());
        stats.put("failed", lambdaQuery().eq(DeviceCommand::getStatus, "failed").count());
        return stats;
    }

    private String trimResult(String resultText) {
        if (resultText == null) {
            return null;
        }
        return resultText.length() > 4000 ? resultText.substring(0, 4000) : resultText;
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
}
