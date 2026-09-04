package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.UserAddress;
import com.juxin.orin.mapper.UserAddressMapper;
import com.juxin.orin.service.IUserAddressService;
import org.springframework.stereotype.Service;

@Service
public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements IUserAddressService {
}
