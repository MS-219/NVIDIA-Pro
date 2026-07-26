package com.juxin.orin.config;

import com.juxin.orin.websocket.RemoteTerminalHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RemoteTerminalHandler remoteTerminalHandler;

    public WebSocketConfig(RemoteTerminalHandler remoteTerminalHandler) {
        this.remoteTerminalHandler = remoteTerminalHandler;
    }

    @Override
    @SuppressWarnings("null")
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 设备端连接端点
        registry.addHandler(remoteTerminalHandler, "/ws/device/*")
                .setAllowedOrigins("*");

        // 管理端连接端点
        registry.addHandler(remoteTerminalHandler, "/ws/admin/terminal/*")
                .setAllowedOrigins("*");
    }
}
