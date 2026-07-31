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
    private final com.juxin.orin.websocket.TerminalHandshakeInterceptor terminalHandshakeInterceptor;

    public WebSocketConfig(
            RemoteTerminalHandler remoteTerminalHandler,
            com.juxin.orin.websocket.TerminalHandshakeInterceptor terminalHandshakeInterceptor) {
        this.remoteTerminalHandler = remoteTerminalHandler;
        this.terminalHandshakeInterceptor = terminalHandshakeInterceptor;
    }

    @Override
    @SuppressWarnings("null")
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 设备端连接端点
        registry.addHandler(remoteTerminalHandler, "/ws/device/*")
                .addInterceptors(terminalHandshakeInterceptor)
                .setAllowedOriginPatterns(
                        "https://nvidia.juxinsuanli.cn",
                        "http://localhost:*",
                        "http://127.0.0.1:*");

        // 管理端连接端点
        registry.addHandler(remoteTerminalHandler, "/ws/admin/terminal/*")
                .addInterceptors(terminalHandshakeInterceptor)
                .setAllowedOriginPatterns(
                        "https://nvidia.juxinsuanli.cn",
                        "http://localhost:*",
                        "http://127.0.0.1:*");
    }
}
