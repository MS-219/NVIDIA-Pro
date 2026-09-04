package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.Notice;
import com.juxin.orin.service.INoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 公告控制器
 */
@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    @Autowired
    private INoticeService noticeService;

    // ========== 小程序端 API ==========

    /**
     * 获取公告列表（小程序首页用）
     */
    @GetMapping("/list")
    public Result<Object> list(@RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(noticeService.getPublishedList(limit));
    }

    /**
     * 获取公告详情
     */
    @GetMapping("/detail/{id}")
    public Result<Notice> detail(@PathVariable Long id) {
        Notice notice = noticeService.getById(id);
        if (notice == null || notice.getStatus() != 1) {
            return Result.error("公告不存在");
        }
        return Result.success(notice);
    }

    // ========== 管理后台 API ==========

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
     * 获取公告列表（管理后台用，分页）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/admin/list")
    public Result<Object> adminList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        Page<Notice> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Notice::getTitle, keyword);
        }
        if (status != null) {
            wrapper.eq(Notice::getStatus, status);
        }

        wrapper.orderByDesc(Notice::getSort)
                .orderByDesc(Notice::getCreateTime);

        return Result.success(noticeService.page(pageParam, wrapper));
    }

    /**
     * 新增公告（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin/add")
    public Result<String> add(
            @RequestBody Notice notice,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        notice.setCreateTime(LocalDateTime.now());
        notice.setUpdateTime(LocalDateTime.now());
        if (notice.getStatus() == null) {
            notice.setStatus(0); // 默认草稿
        }
        if (notice.getStatus() == 1) {
            notice.setPublishTime(LocalDateTime.now());
        }
        if (notice.getSort() == null) {
            notice.setSort(0);
        }
        boolean success = noticeService.save(notice);
        return success ? Result.success("添加成功") : Result.error("添加失败");
    }

    /**
     * 更新公告（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin/update")
    public Result<String> update(
            @RequestBody Notice notice,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        if (notice.getId() == null) {
            return Result.error("公告ID不能为空");
        }
        Notice existing = noticeService.getById(notice.getId());
        if (existing == null) {
            return Result.error("公告不存在");
        }
        if (notice.getStatus() != null && notice.getStatus() == 1) {
            notice.setPublishTime(existing.getPublishTime() == null
                    ? LocalDateTime.now()
                    : existing.getPublishTime());
        }
        notice.setUpdateTime(LocalDateTime.now());
        boolean success = noticeService.updateById(notice);
        return success ? Result.success("更新成功") : Result.error("更新失败");
    }

    /**
     * 发布公告（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin/publish/{id}")
    public Result<String> publish(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        Notice notice = noticeService.getById(id);
        if (notice == null) {
            return Result.error("公告不存在");
        }
        notice.setStatus(1);
        if (notice.getPublishTime() == null) {
            notice.setPublishTime(LocalDateTime.now());
        }
        notice.setUpdateTime(LocalDateTime.now());
        boolean success = noticeService.updateById(notice);
        return success ? Result.success("发布成功") : Result.error("发布失败");
    }

    /**
     * 下架公告（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin/unpublish/{id}")
    public Result<String> unpublish(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        Notice notice = noticeService.getById(id);
        if (notice == null) {
            return Result.error("公告不存在");
        }
        notice.setStatus(0);
        notice.setUpdateTime(LocalDateTime.now());
        boolean success = noticeService.updateById(notice);
        return success ? Result.success("下架成功") : Result.error("下架失败");
    }

    /**
     * 删除公告（管理员专用）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/admin/delete/{id}")
    public Result<String> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String error = validateAdminToken(token);
        if (error != null)
            return Result.error(error);

        boolean success = noticeService.removeById(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }
}
