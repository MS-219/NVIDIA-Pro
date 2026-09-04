package com.juxin.orin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.SysUser;

public interface ISysUserService extends IService<SysUser> {

    /**
     * 管理员登录
     * 
     * @param username 用户名
     * @param password 密码
     * @return JWT Token，登录失败返回 null
     */
    String login(String username, String password);
}
