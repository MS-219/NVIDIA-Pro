package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.SysUser;
import com.juxin.orin.mapper.SysUserMapper;
import com.juxin.orin.service.ISysUserService;
import com.juxin.orin.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String login(String username, String password) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = this.getOne(wrapper);

        if (user == null) {
            return null; // 用户不存在
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null; // 密码错误
        }

        String role = user.getRole() == null || user.getRole().isBlank() ? "admin" : user.getRole();

        // 生成 JWT Token
        return JwtUtil.generateToken(user.getId(), user.getUsername(), "admin", role);
    }
}
