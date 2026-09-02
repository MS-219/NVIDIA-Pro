package com.juxin.orin.app.device;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppNodeRepository {

    List<AppNode> findOwnedBy(long ownerUserId);

    Optional<AppNode> findById(long id);

    Optional<AppNode> findByCode(String code);

    /**
     * Claims an unowned node.  The conditional owner predicate is intentional:
     * two concurrent requests for the same code cannot both claim it.
     */
    boolean claim(long id, long ownerUserId, String name, Instant boundAt);

    /** Releases a node only when it is owned by the requesting user. */
    boolean release(long id, long ownerUserId, Instant releasedAt);

    DashboardAggregate aggregateOwnedBy(long ownerUserId);

    List<EarningSnapshot> earningsOwnedBy(long ownerUserId);

    record DashboardAggregate(
            long total,
            long online,
            BigDecimal totalHashrate,
            BigDecimal todayEarnings,
            BigDecimal totalEarnings) {
    }

    /** Current per-node earning counters until a historical ledger is added. */
    record EarningSnapshot(
            long deviceId,
            String deviceName,
            BigDecimal todayEarnings,
            BigDecimal totalEarnings,
            Instant updatedAt) {
    }
}
