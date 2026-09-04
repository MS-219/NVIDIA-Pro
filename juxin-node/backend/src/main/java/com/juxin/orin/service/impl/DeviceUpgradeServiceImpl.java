package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceCommand;
import com.juxin.orin.entity.DeviceUpgradeBatch;
import com.juxin.orin.entity.DeviceUpgradePackage;
import com.juxin.orin.entity.DeviceUpgradeRecord;
import com.juxin.orin.service.IDeviceCommandService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IDeviceUpgradeBatchService;
import com.juxin.orin.service.IDeviceUpgradePackageService;
import com.juxin.orin.service.IDeviceUpgradeRecordService;
import com.juxin.orin.service.IDeviceUpgradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DeviceUpgradeServiceImpl implements IDeviceUpgradeService {

    private static final int MAX_BATCH_DEVICES = 2000;
    private static final long ACTIVE_UPGRADE_CACHE_TTL_MS = 15000L;

    private final Map<String, Set<String>> activeUpgradeTargets = new ConcurrentHashMap<>();
    private volatile long activeUpgradeCacheAt = 0L;

    @Autowired
    private IDeviceUpgradePackageService packageService;

    @Autowired
    private IDeviceUpgradeBatchService batchService;

    @Autowired
    private IDeviceUpgradeRecordService recordService;

    @Autowired
    private IDeviceCommandService commandService;

    @Autowired
    private IDeviceService deviceService;

    @Override
    @Transactional
    public DeviceUpgradePackage uploadPackage(MultipartFile file, String version, String releaseNote, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择升级包文件");
        }
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("请填写版本号");
        }
        String originalName = file.getOriginalFilename() == null ? "agent-upgrade.tar.gz" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String lowerName = safeName.toLowerCase();
        String suffix = lowerName.endsWith(".tar.gz") ? ".tar.gz"
                : (lowerName.endsWith(".tgz") ? ".tgz" : ".tar.gz");
        String storedName = "agent-" + version.trim().replaceAll("[^A-Za-z0-9._-]", "_")
                + "-" + System.currentTimeMillis() + suffix;

        try {
            Path dir = Path.of(System.getProperty("user.dir"), "uploads", "agent-upgrades");
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName);
            file.transferTo(target.toFile());

            LocalDateTime now = LocalDateTime.now();
            DeviceUpgradePackage pkg = new DeviceUpgradePackage();
            pkg.setPackageNo("PKG" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            pkg.setVersion(version.trim());
            pkg.setFileName(originalName);
            pkg.setFilePath(target.toString());
            pkg.setFileUrl("/api/upgrade-packages/" + storedName);
            pkg.setFileSize(Files.size(target));
            pkg.setChecksum(sha256(target));
            pkg.setStatus("active");
            pkg.setReleaseNote(releaseNote);
            pkg.setUploadedBy(uploadedBy == null || uploadedBy.isBlank() ? "admin" : uploadedBy);
            pkg.setCreateTime(now);
            pkg.setUpdateTime(now);
            packageService.save(pkg);
            return pkg;
        } catch (Exception e) {
            throw new IllegalStateException("升级包保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public DeviceUpgradeBatch createBatch(Map<String, Object> params) {
        Long packageId = getLong(params, "packageId");
        if (packageId == null) {
            throw new IllegalArgumentException("请选择升级包");
        }
        DeviceUpgradePackage pkg = packageService.getById(packageId);
        if (pkg == null || !"active".equals(pkg.getStatus())) {
            throw new IllegalArgumentException("升级包不存在或不可用");
        }

        String targetScope = getString(params, "targetScope");
        if (targetScope == null || targetScope.isBlank()) {
            targetScope = "ALL_ONLINE";
        }
        List<Device> devices = queryTargetDevices(
                targetScope,
                getString(params, "locationKeyword"),
                getString(params, "carrier"),
                Boolean.TRUE.equals(getBoolean(params, "onlyOutdated")),
                pkg.getVersion());
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("目标范围内没有可升级设备");
        }
        if (devices.size() > MAX_BATCH_DEVICES) {
            throw new IllegalArgumentException("一次最多升级 " + MAX_BATCH_DEVICES + " 台设备，请缩小范围");
        }

        LocalDateTime now = LocalDateTime.now();
        DeviceUpgradeBatch batch = new DeviceUpgradeBatch();
        batch.setBatchNo("UPG" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        batch.setPackageId(pkg.getId());
        batch.setTargetVersion(pkg.getVersion());
        batch.setTargetScope(targetScope);
        batch.setLocationKeyword(getString(params, "locationKeyword"));
        batch.setCarrier(getString(params, "carrier"));
        batch.setTotalCount(devices.size());
        batch.setPendingCount(devices.size());
        batch.setDeliveredCount(0);
        batch.setUpgradingCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setSkippedCount(0);
        batch.setStatus("running");
        batch.setRemark(getString(params, "remark"));
        batch.setStartedAt(now);
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        batchService.save(batch);

        for (Device device : devices) {
            DeviceUpgradeRecord record = new DeviceUpgradeRecord();
            record.setBatchId(batch.getId());
            record.setPackageId(pkg.getId());
            record.setDeviceId(device.getId());
            record.setDeviceSn(device.getSn());
            record.setFromVersion(device.getAgentVersion());
            record.setTargetVersion(pkg.getVersion());
            record.setStatus("pending");
            record.setCreateTime(now);
            record.setUpdateTime(now);
            recordService.save(record);

            String payload = "upgradeBatchId=" + batch.getId()
                    + ";upgradeRecordId=" + record.getId()
                    + ";targetVersion=" + pkg.getVersion();
            DeviceCommand command = commandService.dispatchCommand(
                    device.getId(),
                    device.getSn(),
                    "UPGRADE_AGENT",
                    buildUpgradeCommand(pkg, batch),
                    payload,
                    "设备升级 " + pkg.getVersion() + (batch.getRemark() == null ? "" : " / " + batch.getRemark()));
            if (command != null) {
                record.setCommandId(command.getId());
                record.setCommandNo(command.getCommandNo());
                record.setUpdateTime(LocalDateTime.now());
                recordService.updateById(record);
            } else {
                record.setStatus("failed");
                record.setErrorMsg("指令下发失败");
                record.setFinishedAt(LocalDateTime.now());
                record.setUpdateTime(LocalDateTime.now());
                recordService.updateById(record);
            }
        }

        refreshBatchCounters(batch.getId());
        invalidateActiveUpgradeCache();
        return batchService.getById(batch.getId());
    }

    @Override
    public IPage<DeviceUpgradeRecord> listRecords(Integer page, Integer size, Long batchId, String deviceSn, String status) {
        LambdaQueryWrapper<DeviceUpgradeRecord> wrapper = new LambdaQueryWrapper<>();
        if (batchId != null) {
            wrapper.eq(DeviceUpgradeRecord::getBatchId, batchId);
        }
        if (deviceSn != null && !deviceSn.trim().isEmpty()) {
            wrapper.like(DeviceUpgradeRecord::getDeviceSn, deviceSn.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(DeviceUpgradeRecord::getStatus, status.trim());
        }
        wrapper.orderByDesc(DeviceUpgradeRecord::getCreateTime);
        return recordService.page(new Page<>(page, size), wrapper);
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("packages", packageService.lambdaQuery().eq(DeviceUpgradePackage::getStatus, "active").count());
        stats.put("runningBatches", batchService.lambdaQuery().eq(DeviceUpgradeBatch::getStatus, "running").count());
        stats.put("upgrading", recordService.lambdaQuery().eq(DeviceUpgradeRecord::getStatus, "upgrading").count());
        stats.put("success", recordService.lambdaQuery().eq(DeviceUpgradeRecord::getStatus, "success").count());
        stats.put("failed", recordService.lambdaQuery().eq(DeviceUpgradeRecord::getStatus, "failed").count());
        return stats;
    }

    @Override
    @Transactional
    public void handleCommandResult(String commandNo, Integer exitCode, String resultText) {
        if (commandNo == null || commandNo.trim().isEmpty()) {
            return;
        }
        DeviceUpgradeRecord record = recordService.lambdaQuery()
                .eq(DeviceUpgradeRecord::getCommandNo, commandNo.trim())
                .one();
        if (record == null || "success".equals(record.getStatus()) || "failed".equals(record.getStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        record.setDeliveredAt(record.getDeliveredAt() == null ? now : record.getDeliveredAt());
        record.setResultText(trim(resultText, 4000));
        record.setUpdateTime(now);
        if (exitCode != null && exitCode == 0) {
            record.setStatus("upgrading");
            record.setErrorMsg(null);
        } else {
            record.setStatus("failed");
            record.setErrorMsg(extractError(resultText, exitCode));
            record.setFinishedAt(now);
        }
        recordService.updateById(record);
        refreshBatchCounters(record.getBatchId());
        if ("failed".equals(record.getStatus())) {
            removeActiveUpgradeTarget(record.getDeviceSn(), record.getTargetVersion());
        }
    }

    @Override
    @Transactional
    public void markCommandDelivered(String commandNo) {
        if (commandNo == null || commandNo.trim().isEmpty()) {
            return;
        }
        DeviceUpgradeRecord record = recordService.lambdaQuery()
                .eq(DeviceUpgradeRecord::getCommandNo, commandNo.trim())
                .one();
        if (record == null || !"pending".equals(record.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        record.setStatus("delivered");
        record.setDeliveredAt(now);
        record.setUpdateTime(now);
        recordService.updateById(record);
        refreshBatchCounters(record.getBatchId());
    }

    @Override
    @Transactional
    public void markSuccessByHeartbeat(String deviceSn, String agentVersion) {
        if (deviceSn == null || deviceSn.isBlank() || agentVersion == null || agentVersion.isBlank()) {
            return;
        }
        if (!isActiveUpgradeTarget(deviceSn.trim(), agentVersion.trim())) {
            return;
        }
        List<DeviceUpgradeRecord> records = recordService.lambdaQuery()
                .eq(DeviceUpgradeRecord::getDeviceSn, deviceSn.trim())
                .eq(DeviceUpgradeRecord::getTargetVersion, agentVersion.trim())
                .in(DeviceUpgradeRecord::getStatus, java.util.Arrays.asList("pending", "delivered", "upgrading"))
                .list();
        if (records.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Set<Long> batchIds = new HashSet<>();
        for (DeviceUpgradeRecord record : records) {
            record.setStatus("success");
            record.setErrorMsg(null);
            record.setResultText("设备心跳已上报目标版本: " + agentVersion.trim());
            record.setFinishedAt(now);
            record.setUpdateTime(now);
            recordService.updateById(record);
            batchIds.add(record.getBatchId());
        }
        removeActiveUpgradeTarget(deviceSn.trim(), agentVersion.trim());
        for (Long batchId : batchIds) {
            refreshBatchCounters(batchId);
        }
    }

    @Override
    @Transactional
    public void refreshBatchCounters(Long batchId) {
        if (batchId == null) {
            return;
        }
        DeviceUpgradeBatch batch = batchService.getById(batchId);
        if (batch == null) {
            return;
        }
        int pending = countByStatus(batchId, "pending");
        int delivered = countByStatus(batchId, "delivered");
        int upgrading = countByStatus(batchId, "upgrading");
        int success = countByStatus(batchId, "success");
        int failed = countByStatus(batchId, "failed") + countByStatus(batchId, "timeout");
        int skipped = countByStatus(batchId, "skipped");
        int total = Math.toIntExact(recordService.lambdaQuery()
                .eq(DeviceUpgradeRecord::getBatchId, batchId)
                .count());

        batch.setTotalCount(total);
        batch.setPendingCount(pending);
        batch.setDeliveredCount(delivered);
        batch.setUpgradingCount(upgrading);
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setSkippedCount(skipped);
        batch.setUpdateTime(LocalDateTime.now());
        if (total > 0 && success + failed + skipped >= total) {
            batch.setFinishedAt(LocalDateTime.now());
            batch.setStatus(failed > 0 && success > 0 ? "partial" : (failed > 0 ? "failed" : "completed"));
        } else {
            batch.setStatus("running");
        }
        batchService.updateById(batch);
    }

    @Override
    @Transactional
    public int markTimeoutRecords(int timeoutMinutes) {
        int safeTimeout = Math.max(timeoutMinutes, 10);
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(safeTimeout);
        List<DeviceUpgradeRecord> timeoutRecords = recordService.lambdaQuery()
                .in(DeviceUpgradeRecord::getStatus, java.util.Arrays.asList("delivered", "upgrading"))
                .lt(DeviceUpgradeRecord::getUpdateTime, threshold)
                .list();
        if (timeoutRecords.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> batchIds = new HashSet<>();
        for (DeviceUpgradeRecord record : timeoutRecords) {
            record.setStatus("timeout");
            record.setErrorMsg("升级超时，设备未在 " + safeTimeout + " 分钟内上报目标版本");
            record.setFinishedAt(now);
            record.setUpdateTime(now);
            recordService.updateById(record);
            removeActiveUpgradeTarget(record.getDeviceSn(), record.getTargetVersion());
            batchIds.add(record.getBatchId());
        }
        for (Long batchId : batchIds) {
            refreshBatchCounters(batchId);
        }
        return timeoutRecords.size();
    }

    private List<Device> queryTargetDevices(String targetScope, String locationKeyword, String carrier,
                                            boolean onlyOutdated, String targetVersion) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getStatus, 1)
                .eq(Device::getType, 2);
        if ("LOCATION".equals(targetScope)) {
            if (locationKeyword == null || locationKeyword.trim().isEmpty()) {
                throw new IllegalArgumentException("请输入地区关键词");
            }
            wrapper.like(Device::getLocation, locationKeyword.trim());
        } else if ("CARRIER".equals(targetScope)) {
            if (carrier == null || carrier.trim().isEmpty()) {
                throw new IllegalArgumentException("请选择运营商");
            }
            wrapper.eq(Device::getCarrier, carrier.trim());
        } else if (!"ALL_ONLINE".equals(targetScope)) {
            throw new IllegalArgumentException("暂不支持该升级范围");
        }
        if (onlyOutdated && targetVersion != null && !targetVersion.trim().isEmpty()) {
            wrapper.and(w -> w.isNull(Device::getAgentVersion).or().ne(Device::getAgentVersion, targetVersion.trim()));
        }
        wrapper.orderByDesc(Device::getLastHeartbeatTime);
        return deviceService.list(wrapper);
    }

    private String buildUpgradeCommand(DeviceUpgradePackage pkg, DeviceUpgradeBatch batch) {
        String log = "/opt/juxin-orin/runtime/upgrade-" + batch.getBatchNo() + ".log";
        String publicUrl = pkg.getFileUrl().startsWith("http")
                ? pkg.getFileUrl()
                : System.getenv().getOrDefault("ORIN_PUBLIC_BASE_URL", "https://nvidia.juxinsuanli.cn") + pkg.getFileUrl();
        return "set -e; VERSION=" + shellQuote(pkg.getVersion())
                + "; PACKAGE_URL=" + shellQuote(publicUrl)
                + "; EXPECTED_SHA256=" + shellQuote(pkg.getChecksum())
                + "; WORK=/tmp/juxin-orin-upgrade-$VERSION; mkdir -p \"$WORK\" /opt/juxin-orin/runtime; cd \"$WORK\"; "
                + "if command -v curl >/dev/null 2>&1; then curl -fsSL \"$PACKAGE_URL\" -o package.tgz; "
                + "elif command -v wget >/dev/null 2>&1; then wget -q \"$PACKAGE_URL\" -O package.tgz; "
                + "else echo MISSING:curl_or_wget; exit 127; fi; "
                + "if command -v sha256sum >/dev/null 2>&1; then echo \"$EXPECTED_SHA256  package.tgz\" | sha256sum -c -; fi; "
                + "tar -xzf package.tgz; "
                + "if [ ! -f install_agent.sh ]; then found=$(find . -maxdepth 3 -name install_agent.sh | head -1); [ -n \"$found\" ] || { echo MISSING:install_agent.sh; exit 2; }; cd \"$(dirname \"$found\")\"; fi; "
                + "chmod +x install_agent.sh proxy-control.sh tunnel-control.sh 2>/dev/null || true; "
                + "nohup env ORIN_REMOTE_UPGRADE=1 ORIN_TARGET_VERSION=\"$VERSION\" bash ./install_agent.sh >" + shellQuote(log) + " 2>&1 & "
                + "echo ORIN_AGENT_UPGRADE_STARTED; echo VERSION:$VERSION; echo LOG:" + log;
    }

    private int countByStatus(Long batchId, String status) {
        return Math.toIntExact(recordService.lambdaQuery()
                .eq(DeviceUpgradeRecord::getBatchId, batchId)
                .eq(DeviceUpgradeRecord::getStatus, status)
                .count());
    }

    private boolean isActiveUpgradeTarget(String deviceSn, String agentVersion) {
        refreshActiveUpgradeCacheIfNeeded();
        Set<String> versions = activeUpgradeTargets.get(deviceSn);
        return versions != null && versions.contains(agentVersion);
    }

    private void refreshActiveUpgradeCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - activeUpgradeCacheAt < ACTIVE_UPGRADE_CACHE_TTL_MS) {
            return;
        }
        synchronized (activeUpgradeTargets) {
            now = System.currentTimeMillis();
            if (now - activeUpgradeCacheAt < ACTIVE_UPGRADE_CACHE_TTL_MS) {
                return;
            }
            List<DeviceUpgradeRecord> activeRecords = recordService.lambdaQuery()
                    .select(DeviceUpgradeRecord::getDeviceSn, DeviceUpgradeRecord::getTargetVersion)
                    .in(DeviceUpgradeRecord::getStatus, java.util.Arrays.asList("pending", "delivered", "upgrading"))
                    .list();
            Map<String, Set<String>> next = new HashMap<>();
            for (DeviceUpgradeRecord record : activeRecords) {
                if (record.getDeviceSn() == null || record.getTargetVersion() == null) {
                    continue;
                }
                next.computeIfAbsent(record.getDeviceSn(), key -> ConcurrentHashMap.newKeySet()).add(record.getTargetVersion());
            }
            activeUpgradeTargets.clear();
            activeUpgradeTargets.putAll(next);
            activeUpgradeCacheAt = now;
        }
    }

    private void invalidateActiveUpgradeCache() {
        activeUpgradeCacheAt = 0L;
    }

    private void removeActiveUpgradeTarget(String deviceSn, String targetVersion) {
        if (deviceSn == null || targetVersion == null) {
            return;
        }
        Set<String> versions = activeUpgradeTargets.get(deviceSn);
        if (versions == null) {
            return;
        }
        versions.remove(targetVersion);
        if (versions.isEmpty()) {
            activeUpgradeTargets.remove(deviceSn);
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String extractError(String resultText, Integer exitCode) {
        String result = trim(resultText, 500);
        if (result == null || result.isBlank()) {
            return "升级指令失败，退出码 " + (exitCode == null ? "-" : exitCode);
        }
        List<String> lines = result.lines().filter(line -> !line.isBlank()).collect(Collectors.toList());
        return lines.isEmpty() ? result : lines.get(lines.size() - 1);
    }

    private String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(text.length() - max) : text;
    }

    private Long getLong(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    private String getString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean getBoolean(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
