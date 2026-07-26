package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.AiDeviceTask;
import com.juxin.orin.entity.Device;
import com.juxin.orin.mapper.AiDeviceTaskMapper;
import com.juxin.orin.service.IAiDeviceTaskService;
import com.juxin.orin.service.IDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiDeviceTaskServiceImpl extends ServiceImpl<AiDeviceTaskMapper, AiDeviceTask>
        implements IAiDeviceTaskService {

    @Autowired
    private IDeviceService deviceService;

    @Override
    public Page<AiDeviceTask> getAdminTaskList(Integer page, Integer size, String deviceSn, String status) {
        Page<AiDeviceTask> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AiDeviceTask> wrapper = new LambdaQueryWrapper<>();

        if (deviceSn != null && !deviceSn.isEmpty()) {
            wrapper.like(AiDeviceTask::getDeviceSn, deviceSn);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AiDeviceTask::getStatus, status);
        }

        wrapper.orderByDesc(AiDeviceTask::getCreateTime);

        Page<AiDeviceTask> result = this.page(pageParam, wrapper);

        // Fill device info
        fillDeviceInfo(result.getRecords());

        return result;
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalTasks = this.count();
        long completedTasks = this
                .count(new LambdaQueryWrapper<AiDeviceTask>().eq(AiDeviceTask::getStatus, "completed"));
        long failedTasks = this.count(new LambdaQueryWrapper<AiDeviceTask>().eq(AiDeviceTask::getStatus, "failed"));
        long runningTasks = this.count(new LambdaQueryWrapper<AiDeviceTask>().eq(AiDeviceTask::getStatus, "running"));

        List<AiDeviceTask> completedList = this.baseMapper.selectList(new LambdaQueryWrapper<AiDeviceTask>()
                .eq(AiDeviceTask::getStatus, "completed")
                .select(AiDeviceTask::getGenerateTokens, AiDeviceTask::getDurationMs));

        long totalTokens = completedList.stream()
                .mapToLong(t -> t.getGenerateTokens() == null ? 0 : t.getGenerateTokens())
                .sum();

        double totalDurationSeconds = completedList.stream()
                .mapToDouble(t -> t.getDurationMs() == null ? 0 : t.getDurationMs() / 1000.0)
                .sum();

        double avgInferenceRate = totalDurationSeconds > 0 ? (double) totalTokens / totalDurationSeconds : 0.0;
        double avgLatency = completedTasks > 0 ? (double) completedList.stream()
                .mapToLong(t -> t.getDurationMs() == null ? 0 : t.getDurationMs()).sum() / completedTasks : 0.0;

        stats.put("totalTasks", totalTasks);
        stats.put("completedTasks", completedTasks);
        stats.put("failedTasks", failedTasks);
        stats.put("runningTasks", runningTasks);
        stats.put("totalTokens", totalTokens);
        stats.put("avgInferenceRate", String.format("%.1f", avgInferenceRate));
        stats.put("avgLatency", String.format("%.0f", avgLatency));
        stats.put("peakConcurrency", runningTasks); // Simplified for now

        return stats;
    }

    @Override
    public Map<String, Object> getTaskTrend() {
        Map<String, Object> trend = new HashMap<>();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Long> values = new java.util.ArrayList<>();

        for (int i = 11; i >= 0; i--) {
            java.time.LocalDateTime time = now.minusHours(i);
            labels.add(time.getHour() + ":00");
            long count = this.count(new LambdaQueryWrapper<AiDeviceTask>()
                    .eq(AiDeviceTask::getStatus, "completed")
                    .ge(AiDeviceTask::getUpdateTime, time.withMinute(0).withSecond(0))
                    .le(AiDeviceTask::getUpdateTime, time.withMinute(59).withSecond(59)));
            values.add(count);
        }

        trend.put("labels", labels);
        trend.put("values", values);
        return trend;
    }

    private void fillDeviceInfo(List<AiDeviceTask> records) {
        if (records == null || records.isEmpty())
            return;
        List<String> sns = records.stream().map(AiDeviceTask::getDeviceSn).distinct().collect(Collectors.toList());
        if (sns.isEmpty())
            return;

        LambdaQueryWrapper<Device> dw = new LambdaQueryWrapper<>();
        dw.in(Device::getSn, sns);
        List<Device> devices = deviceService.list(dw);
        Map<String, Device> deviceMap = devices.stream().collect(Collectors.toMap(Device::getSn, d -> d));

        for (AiDeviceTask task : records) {
            Device d = deviceMap.get(task.getDeviceSn());
            if (d != null) {
                task.setDeviceName(d.getName());
                task.setDeviceIp(d.getIp());
            }
        }
    }

    @Override
    public List<AiDeviceTask> getLatestTasks(Integer limit) {
        LambdaQueryWrapper<AiDeviceTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiDeviceTask::getCreateTime);
        wrapper.last("LIMIT " + limit);
        List<AiDeviceTask> list = this.list(wrapper);
        fillDeviceInfo(list);
        return list;
    }
}
