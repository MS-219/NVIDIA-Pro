package com.juxin.orin.websocket;

import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程终端 WebSocket 处理器
 * 负责在管理后台和设备 Agent 之间中转消息
 */
@Slf4j
@Component
public class RemoteTerminalHandler extends TextWebSocketHandler {

    private final IDeviceService deviceService;

    public RemoteTerminalHandler(IDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    // 存储设备端的会话: SN -> Session
    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    // 存储管理端的会话: SN -> Session (一个设备同时只能有一个管理员控制)
    private final Map<String, WebSocketSession> adminSessions = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("null")
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getUri() == null)
            return;
        String path = session.getUri().getPath();
        String sn = extractSnFromPath(path);

        if (sn == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        if (path.startsWith("/ws/device/")) {
            // 设备端 Agent 连接
            deviceSessions.put(sn, session);
            markAsEdgeDevice(sn);
            log.info("设备端已连接: SN={}, SessionID={}", sn, session.getId());

            // 如果管理员已在等待，通知管理员设备上线
            WebSocketSession adminSession = adminSessions.get(sn);
            if (adminSession != null && adminSession.isOpen()) {
                adminSession.sendMessage(new TextMessage("\r\n[系统] 设备已上线，准备就绪...\r\n"));
            }
        } else if (path.startsWith("/ws/admin/terminal/")) {
            // 管理员终端连接
            adminSessions.put(sn, session);
            log.info("管理员已连接终端: SN={}, SessionID={}", sn, session.getId());

            // 检查设备是否在线
            WebSocketSession deviceSession = deviceSessions.get(sn);
            if (deviceSession == null || !deviceSession.isOpen()) {
                session.sendMessage(new TextMessage("\r\n[系统] 警告: 设备 Agent 未在线，请检查设备状态。\r\n"));
            } else {
                session.sendMessage(new TextMessage("\r\n[系统] 已连接到设备中转站，正在唤醒设备 shell...\r\n"));
                // 通知设备端准备 shell
                deviceSession.sendMessage(new TextMessage("INIT_SHELL"));
            }
        }
    }

    @Override
    @SuppressWarnings("null")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (session.getUri() == null)
            return;
        String path = session.getUri().getPath();
        String sn = extractSnFromPath(path);

        if (sn == null)
            return;

        if (path.startsWith("/ws/device/")) {
            // 设备端发来的消息 -> 转发给管理员
            WebSocketSession adminSession = adminSessions.get(sn);
            if (adminSession != null && adminSession.isOpen()) {
                adminSession.sendMessage(message);
            }
        } else if (path.startsWith("/ws/admin/terminal/")) {
            // 管理员输入的命令 -> 转发给设备 Agent
            WebSocketSession deviceSession = deviceSessions.get(sn);
            if (deviceSession != null && deviceSession.isOpen()) {
                deviceSession.sendMessage(message);
            }
        }
    }

    @Override
    @SuppressWarnings("null")
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        if (session.getUri() == null)
            return;
        String path = session.getUri().getPath();
        String sn = extractSnFromPath(path);

        if (sn != null) {
            if (path.startsWith("/ws/device/")) {
                deviceSessions.remove(sn);
                log.info("设备端断开连接: SN={}", sn);
                WebSocketSession adminSession = adminSessions.get(sn);
                if (adminSession != null && adminSession.isOpen()) {
                    adminSession.sendMessage(new TextMessage("\r\n[系统] 设备连接已断开。\r\n"));
                }
            } else if (path.startsWith("/ws/admin/terminal/")) {
                adminSessions.remove(sn);
                log.info("管理员退出终端: SN={}", sn);
            }
        }
    }

    private String extractSnFromPath(String path) {
        String[] parts = path.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }
        return null;
    }

    private void markAsEdgeDevice(String sn) {
        Device device = deviceService.lambdaQuery().eq(Device::getSn, sn).one();
        if (device != null && (device.getType() == null || device.getType() != 2)) {
            device.setType(2);
            deviceService.updateById(device);
        }
    }
}
