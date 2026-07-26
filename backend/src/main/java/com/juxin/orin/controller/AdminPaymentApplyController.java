package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.UserContract;
import com.juxin.orin.entity.UserPaymentApply;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.mapper.UserContractMapper;
import com.juxin.orin.mapper.UserPaymentApplyMapper;
import com.juxin.orin.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台审核收款信息变更控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/payment-apply")
public class AdminPaymentApplyController {

    @Autowired
    private UserPaymentApplyMapper applyMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private UserContractMapper contractMapper;

    /**
     * 获取申请列表
     */
    @GetMapping("/list")
    public Result<Page<UserPaymentApply>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestHeader("Authorization") String token) {

        if (!validateAdmin(token)) {
            return Result.error("无权限");
        }

        LambdaQueryWrapper<UserPaymentApply> query = new LambdaQueryWrapper<>();
        if (status != null) {
            query.eq(UserPaymentApply::getStatus, status);
        }
        query.orderByDesc(UserPaymentApply::getCreateTime);

        Page<UserPaymentApply> applyPage = applyMapper.selectPage(new Page<>(page, size), query);

        // 填充用户昵称等信息
        for (UserPaymentApply apply : applyPage.getRecords()) {
            AppUser user = appUserMapper.selectById(apply.getUserId());
            if (user != null) {
                apply.setNickname(user.getNickname());
                // 仅用于前端显示对比的旧账号
                if (apply.getPaymentType() != null) {
                    apply.setOldCardNo(apply.getPaymentType() == 0 ? user.getBankCardNo() : user.getAlipayAccount());
                }
            }
        }
        return Result.success(applyPage);
    }

    /**
     * 审核通过
     */
    @PostMapping("/approve/{id}")
    public Result<String> approve(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {

        if (!validateAdmin(token)) {
            return Result.error("无权限");
        }

        UserPaymentApply apply = applyMapper.selectById(id);
        if (apply == null) {
            return Result.error("申请记录不存在");
        }
        if (apply.getStatus() != 0) {
            return Result.error("该记录已被审核");
        }

        Long userId = apply.getUserId();
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 1. 同步到 app_user
        if (apply.getPaymentType() == 0) {
            user.setBankCardNo(apply.getNewCardNo());
        } else {
            user.setAlipayAccount(apply.getNewCardNo());
        }
        appUserMapper.updateById(user);

        // 2. 如果之前签约了佣金保，同步更新契约表中的卡号
        UserContract contract = contractMapper.selectOne(new LambdaQueryWrapper<UserContract>().eq(UserContract::getUserId, userId));
        if (contract != null) {
            if (apply.getPaymentType() == 0) {
                contract.setBankCardNo(apply.getNewCardNo());
            } else {
                contract.setAlipayAccount(apply.getNewCardNo());
            }
            contract.setPaymentType(apply.getPaymentType());
            contract.setUpdateTime(LocalDateTime.now());
            contractMapper.updateById(contract);
        }

        // 3. 标记为审核通过
        apply.setStatus(1);
        apply.setUpdateTime(LocalDateTime.now());
        applyMapper.updateById(apply);

        return Result.success("已通过审核");
    }

    /**
     * 审核驳回
     */
    @PostMapping("/reject/{id}")
    public Result<String> reject(
            @PathVariable Long id,
            @RequestBody Map<String, String> params,
            @RequestHeader("Authorization") String token) {

        if (!validateAdmin(token)) {
            return Result.error("无权限");
        }

        String reason = params.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            return Result.error("请填写驳回理由");
        }

        UserPaymentApply apply = applyMapper.selectById(id);
        if (apply == null) {
            return Result.error("申请记录不存在");
        }
        if (apply.getStatus() != 0) {
            return Result.error("该记录已被审核");
        }

        // 仅标记为驳回
        apply.setStatus(2);
        apply.setRejectReason(reason);
        apply.setUpdateTime(LocalDateTime.now());
        applyMapper.updateById(apply);

        return Result.success("已驳回该申请");
    }

    private boolean validateAdmin(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return JwtUtil.validateToken(token) && "admin".equals(JwtUtil.getUserType(token));
    }
}
