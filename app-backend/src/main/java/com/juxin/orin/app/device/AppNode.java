package com.juxin.orin.app.device;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A node provisioned for the independent APP.  An unowned row is a node that
 * can still be claimed with its binding code; ownership is never supplied by
 * the client payload.
 */
public record AppNode(
        long id,
        String code,
        Long ownerUserId,
        String name,
        String status,
        BigDecimal hashrate,
        BigDecimal temperature,
        BigDecimal dailyEarnings,
        BigDecimal totalEarnings,
        Instant lastReportedAt,
        Instant boundAt,
        Instant createdAt,
        Instant updatedAt) {
}
