package com.juxin.orin.app.device;

import com.juxin.orin.app.device.AppEdgeController;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppTerminalHandshakeInterceptor implements HandshakeInterceptor {
    static final String ROLE = "terminalRole";
    static final String SN = "deviceSn";
    private static final Pattern ADMIN = Pattern.compile("^/ws/admin/terminal/((?:JD|RK3588)-[A-F0-9]{16})$");
    private static final Pattern DEVICE = Pattern.compile("^/ws/device/((?:JD|RK3588)-[A-F0-9]{16})$");
    private final JdbcTemplate jdbc;
    private final AppTerminalTicketService tickets;

    public AppTerminalHandshakeInterceptor(JdbcTemplate jdbc, AppTerminalTicketService tickets) {
        this.jdbc = jdbc;
        this.tickets = tickets;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        Matcher admin = ADMIN.matcher(uri.getPath());
        if (admin.matches()) {
            String sn = admin.group(1);
            String ticket = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("ticket");
            if (!tickets.consume(ticket, sn)) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
            attributes.put(ROLE, "admin"); attributes.put(SN, sn); return true;
        }
        Matcher device = DEVICE.matcher(uri.getPath());
        if (device.matches()) {
            String token = request.getHeaders().getFirst("X-RK3588-Device-Token");
            if (token == null || token.isBlank() || !validDeviceToken(token, device.group(1))) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED); return false;
            }
            attributes.put(ROLE, "device"); attributes.put(SN, device.group(1)); return true;
        }
        response.setStatusCode(HttpStatus.NOT_FOUND); return false;
    }

    private boolean validDeviceToken(String token, String sn) {
        String hash = sha256(token.trim());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_edge_device WHERE device_sn = ? AND device_token_hash = ?", Integer.class, sn, hash);
        return count != null && count == 1;
    }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { return ""; }
    }

    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {}
}
