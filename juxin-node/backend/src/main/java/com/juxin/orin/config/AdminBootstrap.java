package com.juxin.orin.config;

import com.juxin.orin.entity.SysUser;
import com.juxin.orin.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final ISysUserService userService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String username = System.getenv("ORIN_ADMIN_USERNAME");
        String password = System.getenv("ORIN_ADMIN_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.length() < 12) {
            log.info("Admin bootstrap skipped; configure ORIN_ADMIN_USERNAME and a 12+ character password");
            return;
        }

        if (userService.lambdaQuery().eq(SysUser::getUsername, username.trim()).count() > 0) {
            return;
        }

        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname("Orin Platform Admin");
        user.setRole("admin");
        user.setCreateTime(LocalDateTime.now());
        userService.save(user);
        log.info("Created initial Orin platform administrator: {}", username.trim());
    }
}
