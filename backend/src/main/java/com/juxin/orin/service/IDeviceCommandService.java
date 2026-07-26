package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;

import java.util.List;
import java.util.Map;

public interface IDeviceCommandService extends IService<DeviceCommand> {

    DeviceCommand dispatchCommand(Long deviceId, String deviceSn, String commandType,
                                  String commandText, String commandPayload, String remark);

    List<DeviceCommand> dispatchCommands(List<Device> devices, String commandType,
                                         String commandText, String commandPayload, String remark);

    DeviceCommand takePendingCommand(String deviceSn);

    boolean cancelCommand(Long id);

    boolean submitResult(String commandNo, Integer exitCode, String resultText);

    Map<String, Object> getStats();
}
