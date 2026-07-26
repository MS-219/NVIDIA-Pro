package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.ComputeJob;
import com.juxin.orin.entity.Device;
import com.juxin.orin.mapper.ComputeJobMapper;
import com.juxin.orin.service.IComputeJobService;
import com.juxin.orin.service.IDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ComputeJobServiceImpl extends ServiceImpl<ComputeJobMapper, ComputeJob>
        implements IComputeJobService {

    @Autowired
    private IDeviceService deviceService;

    @Override
    public Page<ComputeJob> getAdminTaskList(Integer page, Integer size, String deviceSn, String status) {
        Page<ComputeJob> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<ComputeJob> wrapper = new LambdaQueryWrapper<>();

        if (deviceSn != null && !deviceSn.isEmpty()) {
            wrapper.like(ComputeJob::getDeviceSn, deviceSn);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ComputeJob::getStatus, status);
        }

        wrapper.orderByDesc(ComputeJob::getCreateTime);

        Page<ComputeJob> result = this.page(pageParam, wrapper);

        // Fill device info
        fillDeviceInfo(result.getRecords());

        return result;
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalTasks = this.count();
        long completedTasks = this
                .count(new LambdaQueryWrapper<ComputeJob>().eq(ComputeJob::getStatus, "completed"));
        long failedTasks = this.count(new LambdaQueryWrapper<ComputeJob>().eq(ComputeJob::getStatus, "failed"));
        long runningTasks = this.count(new LambdaQueryWrapper<ComputeJob>().eq(ComputeJob::getStatus, "running"));

        List<ComputeJob> completedList = this.baseMapper.selectList(new LambdaQueryWrapper<ComputeJob>()
                .eq(ComputeJob::getStatus, "completed")
                .select(ComputeJob::getGenerateTokens, ComputeJob::getDurationMs));

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
            long count = this.count(new LambdaQueryWrapper<ComputeJob>()
                    .eq(ComputeJob::getStatus, "completed")
                    .ge(ComputeJob::getUpdateTime, time.withMinute(0).withSecond(0))
                    .le(ComputeJob::getUpdateTime, time.withMinute(59).withSecond(59)));
            values.add(count);
        }

        trend.put("labels", labels);
        trend.put("values", values);
        return trend;
    }

    private void fillDeviceInfo(List<ComputeJob> records) {
        if (records == null || records.isEmpty())
            return;
        List<String> sns = records.stream().map(ComputeJob::getDeviceSn).distinct().collect(Collectors.toList());
        if (sns.isEmpty())
            return;

        LambdaQueryWrapper<Device> dw = new LambdaQueryWrapper<>();
        dw.in(Device::getSn, sns);
        List<Device> devices = deviceService.list(dw);
        Map<String, Device> deviceMap = devices.stream().collect(Collectors.toMap(Device::getSn, d -> d));

        for (ComputeJob task : records) {
            Device d = deviceMap.get(task.getDeviceSn());
            if (d != null) {
                task.setDeviceName(d.getName());
                task.setDeviceIp(d.getIp());
            }
        }
    }

    @Override
    public List<ComputeJob> getLatestTasks(Integer limit) {
        LambdaQueryWrapper<ComputeJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ComputeJob::getCreateTime);
        wrapper.last("LIMIT " + limit);
        List<ComputeJob> list = this.list(wrapper);
        fillDeviceInfo(list);
        return list;
    }
}
