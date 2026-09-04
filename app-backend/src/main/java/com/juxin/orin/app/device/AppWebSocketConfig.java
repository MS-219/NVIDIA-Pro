package com.juxin.orin.app.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableWebSocket
public class AppWebSocketConfig implements WebSocketConfigurer {
    private final JdbcTemplate jdbc;
    private final AppTerminalTicketService tickets;
    private final ObjectMapper mapper;

    public AppWebSocketConfig(JdbcTemplate jdbc, AppTerminalTicketService tickets, ObjectMapper mapper) {
        this.jdbc = jdbc; this.tickets = tickets; this.mapper = mapper;
    }

    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new AppRemoteTerminalHandler(mapper), "/ws/device/*", "/ws/admin/terminal/*")
                .addInterceptors(new AppTerminalHandshakeInterceptor(jdbc, tickets))
                .setAllowedOriginPatterns("*");
    }
}
