package com.juxin.orin.app.auth;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface SmsChallengeStore {
    boolean sentRecently(String phone, String clientIp, Instant since);

    void save(String phone, String codeHash, Instant expiresAt, String clientIp, String requestId,
              String providerRequestId, Instant createdAt);

    Optional<Challenge> findLatestActive(String phone, Instant now);

    boolean consume(long id, Instant consumedAt);

    /** Commits independently because the caller rejects the login by throwing. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void incrementAttempts(long id);

    record Challenge(long id, String phone, String codeHash, Instant expiresAt, int attempts) {
    }
}
