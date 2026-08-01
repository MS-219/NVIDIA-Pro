package com.juxin.orin.config;

import com.juxin.orin.websocket.RemoteTerminalHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    static final long WEBSOCKET_IDLE_TIMEOUT_MILLIS = 3_600_000L;

    private final RemoteTerminalHandler remoteTerminalHandler;
    private final com.juxin.orin.websocket.TerminalHandshakeInterceptor terminalHandshakeInterceptor;

    public WebSocketConfig(
            RemoteTerminalHandler remoteTerminalHandler,
            com.juxin.orin.websocket.TerminalHandshakeInterceptor terminalHandshakeInterceptor) {
        this.remoteTerminalHandler = remoteTerminalHandler;
        this.terminalHandshakeInterceptor = terminalHandshakeInterceptor;
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxSessionIdleTimeout(WEBSOCKET_IDLE_TIMEOUT_MILLIS);
        container.setMaxTextMessageBufferSize(65 * 1024);
        return container;
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
