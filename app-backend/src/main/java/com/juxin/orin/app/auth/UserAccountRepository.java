package com.juxin.orin.app.auth;

import java.util.Optional;

public interface UserAccountRepository {
    Optional<UserAccount> findByPhone(String phone);

    Optional<UserAccount> findById(long id);

    UserAccount create(String phone, String nickname);

    Optional<UserAccount> updateNickname(long id, String nickname);
}
