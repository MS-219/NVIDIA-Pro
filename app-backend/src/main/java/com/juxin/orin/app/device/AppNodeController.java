package com.juxin.orin.app.device;

import com.juxin.orin.app.auth.BearerTokenFilter;
import com.juxin.orin.app.common.ApiException;
import com.juxin.orin.app.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/app")
public class AppNodeController {
    private final AppNodeService service;

    public AppNodeController(AppNodeService service) {
        this.service = service;
    }

    @GetMapping("/devices")
    public ApiResponse<List<DeviceResponse>> devices(HttpServletRequest request) {
        long userId = requireUserId(request);
        List<DeviceResponse> devices = service.list(userId).stream().map(DeviceResponse::from).toList();
        return ApiResponse.success(devices);
    }

    @PostMapping("/devices/bind")
    public ApiResponse<DeviceResponse> bind(@Valid @RequestBody BindRequest request,
                                            HttpServletRequest servletRequest) {
        long userId = requireUserId(servletRequest);
        AppNode node = service.bind(userId, request.code(), request.name());
        return ApiResponse.success(DeviceResponse.from(node));
    }

    @DeleteMapping("/devices/{id}")
    public ApiResponse<Void> remove(@PathVariable long id, HttpServletRequest request) {
        service.remove(requireUserId(request), id);
        return ApiResponse.success();
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<DashboardResponse> summary(HttpServletRequest request) {
        AppNodeRepository.DashboardAggregate aggregate = service.summary(requireUserId(request));
        return ApiResponse.success(new DashboardResponse(
                aggregate.total(), aggregate.online(), aggregate.totalHashrate(),
                aggregate.todayEarnings(), aggregate.totalEarnings()));
    }

    @GetMapping("/earnings")
    public ApiResponse<AppNodeService.EarningsSummary> earnings(HttpServletRequest request) {
        return ApiResponse.success(service.earnings(requireUserId(request)));
    }

    private static long requireUserId(HttpServletRequest request) {
        Object value = request.getAttribute(BearerTokenFilter.USER_ID_ATTRIBUTE);
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            throw new ApiException(401, "登录已过期，请重新登录");
        }
        return number.longValue();
    }

    public record BindRequest(
            @NotBlank(message = "设备绑定码不能为空")
            @Size(min = 6, max = 64, message = "设备绑定码需为 6-64 位")
            @Pattern(regexp = "\\s*[A-Za-z0-9][A-Za-z0-9-]{5,63}\\s*",
                    message = "设备绑定码需为 6-64 位字母、数字或短横线")
            String code,
            @Size(max = 80, message = "设备名称长度不能超过 80 个字符")
            String name) {
    }

    public record DeviceResponse(
            long id,
            String code,
            String name,
            String status,
            BigDecimal hashrate,
            BigDecimal temperature,
            BigDecimal dailyEarnings,
            BigDecimal totalEarnings,
            Instant lastReportedAt,
            Instant boundAt) {
        static DeviceResponse from(AppNode node) {
            return new DeviceResponse(node.id(), node.code(), node.name(), node.status(), node.hashrate(),
                    node.temperature(), node.dailyEarnings(), node.totalEarnings(), node.lastReportedAt(), node.boundAt());
        }
    }

    public record DashboardResponse(
            long total,
            long online,
            BigDecimal totalHashrate,
            BigDecimal todayEarnings,
            BigDecimal totalEarnings) {
    }
}
