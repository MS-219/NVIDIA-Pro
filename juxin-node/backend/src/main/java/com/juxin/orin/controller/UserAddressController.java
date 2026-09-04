package com.juxin.orin.controller;


import com.juxin.orin.entity.UserAddress;
import com.juxin.orin.service.IUserAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收货地址管理 - 小程序端接口
 */
@RestController
@RequestMapping("/api/address")
public class UserAddressController {

    @Autowired
    private IUserAddressService addressService;

    /**
     * 获取用户地址列表
     */
    @GetMapping("/list")
    public Map<String, Object> getAddresses(@RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<UserAddress> addresses = addressService.lambdaQuery()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getIsDefault)
                    .orderByDesc(UserAddress::getUpdateTime)
                    .list();

            result.put("code", 200);
            result.put("data", addresses);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取地址列表失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 新增/编辑地址
     */
    @PostMapping("/save")
    public Map<String, Object> saveAddress(@RequestBody UserAddress address) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (address.getUserId() == null) {
                result.put("code", 400);
                result.put("msg", "用户ID不能为空");
                return result;
            }

            // 如果设为默认，先清除其他默认
            if (address.getIsDefault() != null && address.getIsDefault() == 1) {
                addressService.lambdaUpdate()
                        .set(UserAddress::getIsDefault, 0)
                        .eq(UserAddress::getUserId, address.getUserId())
                        .update();
            }

            if (address.getId() != null) {
                // 编辑
                address.setUpdateTime(LocalDateTime.now());
                addressService.updateById(address);
            } else {
                // 新增
                address.setCreateTime(LocalDateTime.now());
                address.setUpdateTime(LocalDateTime.now());
                // 如果是第一个地址，自动设为默认
                long count = addressService.lambdaQuery()
                        .eq(UserAddress::getUserId, address.getUserId())
                        .count();
                if (count == 0) {
                    address.setIsDefault(1);
                }
                addressService.save(address);
            }

            result.put("code", 200);
            result.put("msg", "保存成功");
            result.put("data", address);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "保存地址失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除地址
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteAddress(@PathVariable Long id, @RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            UserAddress address = addressService.getById(id);
            if (address == null || !address.getUserId().equals(userId)) {
                result.put("code", 404);
                result.put("msg", "地址不存在");
                return result;
            }
            addressService.removeById(id);
            result.put("code", 200);
            result.put("msg", "删除成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "删除失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 设为默认地址
     */
    @PostMapping("/setDefault")
    public Map<String, Object> setDefault(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = Long.valueOf(params.get("userId").toString());
            Long addressId = Long.valueOf(params.get("addressId").toString());

            // 先清除所有默认
            addressService.lambdaUpdate()
                    .set(UserAddress::getIsDefault, 0)
                    .eq(UserAddress::getUserId, userId)
                    .update();

            // 设置新默认
            addressService.lambdaUpdate()
                    .set(UserAddress::getIsDefault, 1)
                    .eq(UserAddress::getId, addressId)
                    .eq(UserAddress::getUserId, userId)
                    .update();

            result.put("code", 200);
            result.put("msg", "设置成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "设置失败: " + e.getMessage());
        }
        return result;
    }
}
