package com.juxin.orin.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RemoteTerminalHandler extends TextWebSocketHandler {

    private static final int MAX_ADMIN_INPUT = 16 * 1024;
    private static final int MAX_DEVICE_OUTPUT = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> adminSessions = new ConcurrentHashMap<>();

    public RemoteTerminalHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String role = attribute(session, TerminalHandshakeInterceptor.ROLE_ATTRIBUTE);
        String sn = attribute(session, TerminalHandshakeInterceptor.SN_ATTRIBUTE);
        if (role == null || sn == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.setTextMessageSizeLimit(MAX_DEVICE_OUTPUT + 1024);
        if ("device".equals(role)) {
            WebSocketSession previous = deviceSessions.put(sn, session);
            closeIfReplaced(previous, session);
            log.info("设备终端通道已连接: SN={}, SessionID={}", sn, session.getId());
            WebSocketSession admin = openSession(adminSessions.get(sn));
            if (admin != null) {
                sendSystem(admin, "设备终端通道已恢复", "info");
                sendJson(session, Map.of("type", "open", "cols", 80, "rows", 24));
            }
            return;
        }

        WebSocketSession existing = openSession(adminSessions.putIfAbsent(sn, session));
        if (existing != null && existing != session) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (existing == null) {
            adminSessions.put(sn, session);
        }
        log.info("管理员终端已连接: SN={}, SessionID={}", sn, session.getId());
        WebSocketSession device = openSession(deviceSessions.get(sn));
        if (device == null) {
            sendSystem(session, "设备终端通道未在线", "error");
            return;
        }
        sendSystem(session, "安全通道已建立，正在打开维护终端", "info");
        sendJson(device, Map.of("type", "open", "cols", 80, "rows", 24));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String role = attribute(session, TerminalHandshakeInterceptor.ROLE_ATTRIBUTE);
        String sn = attribute(session, TerminalHandshakeInterceptor.SN_ATTRIBUTE);
        if (role == null || sn == null || message.getPayloadLength() > MAX_DEVICE_OUTPUT) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.getPayload());
        } catch (IOException exception) {
            rejectMessage(session, role, "终端消息格式错误");
            return;
        }
        if ("admin".equals(role)) {
            if (!validAdminMessage(payload)) {
                rejectMessage(session, role, "终端输入消息不被允许");
                return;
            }
            WebSocketSession device = openSession(deviceSessions.get(sn));
            if (device == null) {
                sendSystem(session, "设备终端通道已断开", "error");
                return;
            }
            sendText(device, message);
            return;
        }
        if (!validDeviceMessage(payload)) {
            rejectMessage(session, role, "设备终端消息不被允许");
            return;
        }
        WebSocketSession admin = openSession(adminSessions.get(sn));
        if (admin != null) {
            sendText(admin, message);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("终端传输异常: SessionID={}, error={}", session.getId(), exception.getMessage());
        cleanup(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        cleanup(session);
    }

    public boolean isDeviceConnected(String sn) {
        return openSession(deviceSessions.get(sn)) != null;
    }

    public boolean isAdminConnected(String sn) {
        return openSession(adminSessions.get(sn)) != null;
    }

    private void cleanup(WebSocketSession session) throws IOException {
        String role = attribute(session, TerminalHandshakeInterceptor.ROLE_ATTRIBUTE);
        String sn = attribute(session, TerminalHandshakeInterceptor.SN_ATTRIBUTE);
        if (sn == null) {
            return;
        }
        if ("device".equals(role) && deviceSessions.remove(sn, session)) {
            log.info("设备终端通道已断开: SN={}", sn);
            WebSocketSession admin = openSession(adminSessions.get(sn));
            if (admin != null) {
                sendSystem(admin, "设备终端通道已断开", "error");
            }
        } else if ("admin".equals(role) && adminSessions.remove(sn, session)) {
            log.info("管理员终端已退出: SN={}", sn);
            WebSocketSession device = openSession(deviceSessions.get(sn));
            if (device != null) {
                sendJson(device, Map.of("type", "close"));
            }
        }
    }

    private boolean validAdminMessage(JsonNode payload) {
        String type = payload.path("type").asText("");
        if ("input".equals(type)) {
            return payload.path("data").isTextual()
                    && payload.path("data").textValue().length() <= MAX_ADMIN_INPUT;
        }
        if ("resize".equals(type)) {
            int columns = payload.path("cols").asInt(0);
            int rows = payload.path("rows").asInt(0);
            return columns >= 20 && columns <= 400 && rows >= 5 && rows <= 200;
        }
        return false;
    }

    private boolean validDeviceMessage(JsonNode payload) {
        String type = payload.path("type").asText("");
        if ("output".equals(type)) {
            return payload.path("data").isTextual()
                    && payload.path("data").textValue().length() <= MAX_DEVICE_OUTPUT;
        }
        if ("status".equals(type)) {
            String status = payload.path("status").asText("");
            return status.matches("ready|closed|error");
        }
        return false;
    }

    private void rejectMessage(WebSocketSession session, String role, String message) throws IOException {
        if ("admin".equals(role)) {
            sendSystem(session, message, "error");
        }
    }

    private void sendSystem(WebSocketSession session, String message, String level) throws IOException {
        sendJson(session, Map.of("type", "system", "level", level, "message", message));
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) throws IOException {
        sendText(session, new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private void sendText(WebSocketSession session, TextMessage message) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        }
    }

    private WebSocketSession openSession(WebSocketSession session) {
        return session != null && session.isOpen() ? session : null;
    }

    private void closeIfReplaced(WebSocketSession previous, WebSocketSession current) throws IOException {
        if (previous != null && previous != current && previous.isOpen()) {
            previous.close(CloseStatus.SERVICE_RESTARTED);
        }
    }

    private String attribute(WebSocketSession session, String name) {
        Object value = session.getAttributes().get(name);
        return value == null ? null : value.toString();
    }
}
