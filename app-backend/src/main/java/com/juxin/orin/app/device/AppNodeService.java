package com.juxin.orin.app.device;

import com.juxin.orin.app.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AppNodeService {
    private static final String DEFAULT_NODE_NAME = "Orin 节点";
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{5,63}");

    private final AppNodeRepository repository;
    private final Clock clock;

    @Autowired
    public AppNodeService(AppNodeRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AppNodeService(AppNodeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<AppNode> list(long ownerUserId) {
        requirePositiveUserId(ownerUserId);
        return repository.findOwnedBy(ownerUserId);
    }

    @Transactional
    public AppNode bind(long ownerUserId, String rawCode, String rawName) {
        requirePositiveUserId(ownerUserId);
        String code = normalizeCode(rawCode);
        AppNode node = repository.findByCode(code)
                .orElseThrow(() -> new ApiException(404, "设备绑定码不存在"));
        if (node.ownerUserId() != null) {
            throw new ApiException(409, "设备已被绑定");
        }

        String name = normalizeName(rawName, node.name());
        Instant now = Instant.now(clock);
        if (!repository.claim(node.id(), ownerUserId, name, now)) {
            // Another request may have claimed the row after the initial read.
            throw new ApiException(409, "设备已被绑定");
        }
        return repository.findById(node.id())
                .orElseThrow(() -> new ApiException(500, "绑定后无法读取设备"));
    }

    @Transactional
    public void remove(long ownerUserId, long nodeId) {
        requirePositiveUserId(ownerUserId);
        if (nodeId <= 0) {
            throw new ApiException(404, "设备不存在");
        }
        if (!repository.release(nodeId, ownerUserId, Instant.now(clock))) {
            throw new ApiException(404, "设备不存在");
        }
    }

    public AppNodeRepository.DashboardAggregate summary(long ownerUserId) {
        requirePositiveUserId(ownerUserId);
        return repository.aggregateOwnedBy(ownerUserId);
    }

    public EarningsSummary earnings(long ownerUserId) {
        requirePositiveUserId(ownerUserId);
        List<AppNodeRepository.EarningSnapshot> snapshots = repository.earningsOwnedBy(ownerUserId);
        BigDecimal today = snapshots.stream()
                .map(AppNodeRepository.EarningSnapshot::todayEarnings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = snapshots.stream()
                .map(AppNodeRepository.EarningSnapshot::totalEarnings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<EarningItem> items = snapshots.stream()
                .map(item -> new EarningItem(item.deviceId(), item.deviceName(), item.todayEarnings(),
                        item.totalEarnings(), item.updatedAt()))
                .toList();
        return new EarningsSummary(items, today, total);
    }

    static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ApiException(400, "设备绑定码不能为空");
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!SAFE_CODE.matcher(code).matches()) {
            throw new ApiException(400, "设备绑定码需为 6-64 位字母、数字或短横线");
        }
        return code;
    }

    private static String normalizeName(String rawName, String provisionedName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            name = provisionedName == null || provisionedName.isBlank() ? DEFAULT_NODE_NAME : provisionedName.trim();
        }
        if (name.length() > 80) {
            throw new ApiException(400, "设备名称长度不能超过 80 个字符");
        }
        return name;
    }

    private static void requirePositiveUserId(long userId) {
        if (userId <= 0) {
            throw new ApiException(401, "登录已过期，请重新登录");
        }
    }

    public record EarningItem(
            long deviceId,
            String deviceName,
            BigDecimal todayEarnings,
            BigDecimal totalEarnings,
            java.time.Instant updatedAt) {
    }

    public record EarningsSummary(
            List<EarningItem> items,
            BigDecimal todayEarnings,
            BigDecimal totalEarnings) {
    }
}
