package com.juxin.orin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.juxin.orin.common.Result;
import com.juxin.orin.config.AdminAuthValidator;
import com.juxin.orin.entity.ImageLicense;
import com.juxin.orin.entity.ImageLicenseActivation;
import com.juxin.orin.service.IImageLicenseService;
import com.juxin.orin.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/image-licenses")
public class AdminImageLicenseController {

    @Autowired
    private IImageLicenseService imageLicenseService;

    @Autowired
    private AdminAuthValidator adminAuthValidator;

    @GetMapping("/list")
    public Result<IPage<ImageLicense>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminRole(token);
        if (error != null) {
            return Result.error(error);
        }
        return Result.success(imageLicenseService.listLicenses(page, size, status, keyword));
    }

    @PostMapping("/create")
    public Result<ImageLicense> create(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminRole(token);
        if (error != null) {
            return Result.error(error);
        }
        try {
            String rawToken = adminAuthValidator.normalizeToken(token);
            String createdBy = rawToken == null ? null : JwtUtil.getUsername(rawToken);
            return Result.success(imageLicenseService.createLicense(params, createdBy));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("创建镜像授权失败");
        }
    }

    @PostMapping("/revoke/{id}")
    public Result<String> revoke(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminRole(token);
        if (error != null) {
            return Result.error(error);
        }
        boolean success = imageLicenseService.revokeLicense(id);
        return success ? Result.success("镜像授权已销毁") : Result.error("镜像授权不存在或销毁失败");
    }

    @PostMapping("/{id}/factory")
    public Result<ImageLicense> assignFactory(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminRole(token);
        if (error != null) {
            return Result.error(error);
        }
        try {
            String factoryUsername = params == null || params.get("factoryUsername") == null
                    ? null
                    : params.get("factoryUsername").toString();
            return Result.success(imageLicenseService.assignFactory(id, factoryUsername));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("绑定工厂账号失败");
        }
    }

    @GetMapping("/{id}/devices")
    public Result<IPage<ImageLicenseActivation>> devices(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminRole(token);
        if (error != null) {
            return Result.error(error);
        }
        return Result.success(imageLicenseService.listActivations(id, page, size));
    }

    private String validateAdminRole(String token) {
        String authError = adminAuthValidator.validate(token);
        if (authError != null) {
            return authError;
        }

        String rawToken = adminAuthValidator.normalizeToken(token);
        String role = rawToken == null ? null : JwtUtil.getRole(rawToken);
        if (role == null || role.isBlank()) {
            role = "admin";
        }
        if (!"admin".equals(role)) {
            return "无权限管理镜像授权";
        }
        return null;
    }
}
