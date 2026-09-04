package com.juxin.orin.controller;

import com.juxin.orin.common.Result;
import com.juxin.orin.config.AdminAuthValidator;
import com.juxin.orin.websocket.RemoteTerminalHandler;
import com.juxin.orin.websocket.TerminalTicketService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/admin/terminal")
public class AdminTerminalController {

    private static final Pattern SN_PATTERN = Pattern.compile("(?:ORIN|RK3588|JD)-[A-F0-9]{12,32}");

    private final AdminAuthValidator adminAuthValidator;
    private final TerminalTicketService ticketService;
    private final RemoteTerminalHandler terminalHandler;

    public AdminTerminalController(
            AdminAuthValidator adminAuthValidator,
            TerminalTicketService ticketService,
            RemoteTerminalHandler terminalHandler) {
        this.adminAuthValidator = adminAuthValidator;
        this.ticketService = ticketService;
        this.terminalHandler = terminalHandler;
    }

    @PostMapping("/ticket/{sn}")
    public Result<Map<String, Object>> issueTicket(
            @PathVariable String sn,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String error = adminAuthValidator.validate(authorization);
        if (error != null) {
            return Result.error(401, error);
        }
        if (sn == null || !SN_PATTERN.matcher(sn).matches()) {
            return Result.error(400, "设备序列号格式错误");
        }
        if (!terminalHandler.isDeviceConnected(sn)) {
            return Result.error(409, "设备终端通道未在线，请等待 Agent 连接");
        }
        if (terminalHandler.isAdminConnected(sn)) {
            return Result.error(409, "该设备已有管理员正在使用远程终端");
        }
        TerminalTicketService.IssuedTicket issued = ticketService.issue(
                adminAuthValidator.getAdminId(authorization), sn);
        return Result.success(Map.of(
                "ticket", issued.ticket(),
                "expiresAt", issued.expiresAtEpochMs()));
    }
}
