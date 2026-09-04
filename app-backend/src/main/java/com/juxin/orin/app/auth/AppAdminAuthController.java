package com.juxin.orin.app.auth;

import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import com.juxin.orin.app.config.AppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AppAdminAuthController {
    private final AppProperties properties;
    private final JwtService jwtService;

    public AppAdminAuthController(AppProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        String expectedUser = properties.getAdmin().getUsername();
        String expectedPassword = properties.getAdmin().getPassword();
        if (expectedUser == null || expectedUser.isBlank() || expectedPassword == null || expectedPassword.isBlank()) {
            throw new ApiException(503, "管理账号未配置");
        }
        if (!expectedUser.equals(request.username()) || !expectedPassword.equals(request.password())) {
            throw new ApiException(401, "管理员账号或密码错误");
        }
        return ApiResponse.success(Map.of("token", jwtService.issueAdmin(expectedUser), "username", expectedUser));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, String>> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        var claims = jwtService.requireClaims(authorization);
        if (!"app-admin".equals(claims.get("userType", String.class))) {
            throw new ApiException(403, "需要管理员权限");
        }
        return ApiResponse.success(Map.of("username", claims.get("username", String.class)));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
