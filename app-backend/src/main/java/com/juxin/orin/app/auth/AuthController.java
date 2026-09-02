package com.juxin.orin.app.auth;

import com.juxin.orin.app.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    /** Accepts the same mainland mobile formats as PhoneNumberNormalizer. */
    private static final String PHONE_PATTERN =
            "\\s*(?:(?:\\+?86|0086)[ -]*)?1[3-9](?:[ -]*\\d){9}\\s*";

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/sms/send")
    public ApiResponse<AuthService.SendCodeResult> sendCode(
            @Valid @RequestBody SendCodeRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.sendLoginCode(request.phone(), clientIp(servletRequest)));
    }

    @PostMapping("/sms/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.phone(), request.code(), request.nickname());
        return ApiResponse.success(new LoginResponse(
                result.token(),
                result.user().id(),
                result.user().phone(),
                result.user().nickname()));
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        long userId = jwtService.requireUserId(authorization);
        UserAccount user = authService.findUser(userId);
        return ApiResponse.success(new LoginResponse("", user.id(), user.phone(), user.nickname()));
    }

    @PatchMapping("/me")
    public ApiResponse<LoginResponse> updateMe(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        long userId = jwtService.requireUserId(authorization);
        UserAccount user = authService.updateNickname(userId, request.nickname());
        return ApiResponse.success(new LoginResponse("", user.id(), user.phone(), user.nickname()));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record SendCodeRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = PHONE_PATTERN, message = "手机号格式不正确")
            String phone) {
    }

    public record LoginRequest(
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = PHONE_PATTERN, message = "手机号格式不正确") String phone,
            @NotBlank(message = "验证码不能为空") String code,
            String nickname) {
    }

    public record LoginResponse(String token, long userId, String phone, String nickname) {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "昵称不能为空")
            @jakarta.validation.constraints.Size(max = 40, message = "昵称长度不能超过 40 个字符")
            String nickname) {
    }
}
