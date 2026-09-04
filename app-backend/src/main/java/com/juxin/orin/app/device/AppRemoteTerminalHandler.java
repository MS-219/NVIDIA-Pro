package com.juxin.orin.app.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppRemoteTerminalHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AppRemoteTerminalHandler.class);
    private final ObjectMapper mapper;
    private final Map<String, WebSocketSession> devices = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> admins = new ConcurrentHashMap<>();

    public AppRemoteTerminalHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String role = attr(session, AppTerminalHandshakeInterceptor.ROLE);
        String sn = attr(session, AppTerminalHandshakeInterceptor.SN);
        session.setTextMessageSizeLimit(64 * 1024);
        if ("device".equals(role)) {
            WebSocketSession old = devices.put(sn, session); close(old);
            WebSocketSession admin = open(admins.get(sn));
            if (admin != null) { send(admin, Map.of("type", "system", "level", "info", "message", "设备终端通道已连接")); send(session, Map.of("type", "open", "cols", 80, "rows", 24)); }
        } else if ("admin".equals(role)) {
            if (open(admins.putIfAbsent(sn, session)) != null) { session.close(CloseStatus.POLICY_VIOLATION); return; }
            WebSocketSession device = open(devices.get(sn));
            if (device == null) { send(session, Map.of("type", "system", "level", "error", "message", "设备终端通道未在线")); return; }
            send(session, Map.of("type", "system", "level", "info", "message", "安全终端已连接")); send(device, Map.of("type", "open", "cols", 80, "rows", 24));
        }
    }

    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String role = attr(session, AppTerminalHandshakeInterceptor.ROLE);
        String sn = attr(session, AppTerminalHandshakeInterceptor.SN);
        JsonNode payload;
        try { payload = mapper.readTree(message.getPayload()); } catch (IOException error) { return; }
        String type = payload.path("type").asText("");
        if ("admin".equals(role) && (("input".equals(type) && payload.path("data").isTextual() && payload.path("data").textValue().length() <= 16384) || ("resize".equals(type) && payload.path("cols").asInt() >= 20))) send(open(devices.get(sn)), message);
        if ("device".equals(role) && (("output".equals(type) && payload.path("data").isTextual()) || ("status".equals(type) && payload.path("status").asText("").matches("ready|closed|error")))) send(open(admins.get(sn)), message);
    }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception { cleanup(session); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception { cleanup(session); }

    private void cleanup(WebSocketSession session) throws IOException {
        String role = attr(session, AppTerminalHandshakeInterceptor.ROLE), sn = attr(session, AppTerminalHandshakeInterceptor.SN);
        if ("device".equals(role)) { devices.remove(sn, session); send(open(admins.get(sn)), Map.of("type", "system", "level", "error", "message", "设备终端通道已断开")); }
        if ("admin".equals(role)) { admins.remove(sn, session); send(open(devices.get(sn)), Map.of("type", "close")); }
    }
    private void send(WebSocketSession session, Object payload) throws IOException { if (session == null || !session.isOpen()) return; synchronized (session) { session.sendMessage(new TextMessage(payload instanceof TextMessage m ? m.getPayload() : mapper.writeValueAsString(payload))); } }
    private static WebSocketSession open(WebSocketSession session) { return session != null && session.isOpen() ? session : null; }
    private static void close(WebSocketSession session) throws IOException { if (session != null && session.isOpen()) session.close(CloseStatus.SERVICE_RESTARTED); }
    private static String attr(WebSocketSession session, String key) { Object value = session.getAttributes().get(key); return value == null ? "" : value.toString(); }
}
