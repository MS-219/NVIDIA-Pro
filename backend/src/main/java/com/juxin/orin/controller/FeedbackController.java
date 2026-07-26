package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.UserFeedback;
import com.juxin.orin.mapper.UserFeedbackMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户反馈控制器
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private UserFeedbackMapper feedbackMapper;

    @Autowired
    private com.juxin.orin.service.IAppUserService appUserService;

    /**
     * 提交反馈（小程序使用）
     */
    @PostMapping("/submit")
    public Result<String> submit(@RequestBody UserFeedback feedback) {
        if (feedback.getContent() == null || feedback.getContent().isEmpty()) {
            return Result.error("反馈内容不能为空");
        }
        feedback.setStatus(0); // 待处理
        feedback.setCreateTime(LocalDateTime.now());
        feedback.setUpdateTime(LocalDateTime.now());

        int rows = feedbackMapper.insert(feedback);
        return rows > 0 ? Result.success("提交成功，感谢您的反馈") : Result.error("提交失败，请稍后重试");
    }

    /**
     * 验证管理员权限的辅助方法
     */
    private String validateAdminToken(String token) {
        if (token == null || token.isEmpty())
            return "未登录，请先登录";
        if (token.startsWith("Bearer "))
            token = token.substring(7);
        if (!com.juxin.orin.util.JwtUtil.validateToken(token))
            return "登录已过期，请重新登录";
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType))
            return "无权限访问此接口";
        return null;
    }

    /**
     * 获取反馈列表（管理后台使用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/list")
    public Result<Page<UserFeedback>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        Page<UserFeedback> pageParam = new Page<>(page, size);
        Page<UserFeedback> result = feedbackMapper.selectPage(pageParam,
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserFeedback>()
                        .eq(status != null, "status", status)
                        .orderByDesc("create_time"));

        // 填充用户信息
        for (UserFeedback record : result.getRecords()) {
            if (record.getUserId() != null) {
                com.juxin.orin.entity.AppUser user = appUserService.getById(record.getUserId());
                if (user != null) {
                    record.setNickname(user.getNickname());
                    record.setAvatarUrl(user.getAvatarUrl());
                }
            }
        }
        return Result.success(result);
    }

    /**
     * 处理反馈（管理后台使用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/process")
    public Result<String> process(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        Long id = Long.valueOf(params.get("id").toString());
        Integer status = (Integer) params.get("status");
        String reply = (String) params.get("reply");

        UserFeedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return Result.error("反馈记录不存在");
        }

        feedback.setStatus(status);
        feedback.setReply(reply);
        feedback.setUpdateTime(LocalDateTime.now());

        int rows = feedbackMapper.updateById(feedback);
        return rows > 0 ? Result.success("处理成功") : Result.error("处理失败");
    }

    /**
     * 删除反馈（管理后台使用）
     */
    @PostMapping("/delete/{id}")
    public Result<String> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        UserFeedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return Result.error("反馈记录不存在");
        }

        int rows = feedbackMapper.deleteById(id);
        return rows > 0 ? Result.success("删除成功") : Result.error("删除失败");
    }
}
