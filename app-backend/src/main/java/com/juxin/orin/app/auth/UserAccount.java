package com.juxin.orin.app.auth;

import java.time.Instant;

public record UserAccount(long id, String phone, String nickname, Instant createdAt) {
}
