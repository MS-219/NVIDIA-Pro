package com.juxin.orin.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RemoteTerminalHandlerTest {

    private static final String SN = "ORIN-0123456789AB";

    @Mock
    private WebSocketSession device;

    @Mock
    private WebSocketSession admin;

    @Mock
    private WebSocketSession secondAdmin;

    private RemoteTerminalHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RemoteTerminalHandler(new ObjectMapper());
        configure(device, "device", "device-session");
        configure(admin, "admin", "admin-session");
        configure(secondAdmin, "admin", "admin-session-2");
    }

    @Test
    void authenticatedSessionsRelayStructuredTerminalMessages() throws Exception {
        handler.afterConnectionEstablished(device);
        handler.afterConnectionEstablished(admin);

        handler.handleTextMessage(admin, new TextMessage("{\"type\":\"input\",\"data\":\"uname -a\\r\"}"));
        handler.handleTextMessage(device, new TextMessage("{\"type\":\"output\",\"data\":\"Linux orin\"}"));

        ArgumentCaptor<TextMessage> deviceMessages = ArgumentCaptor.forClass(TextMessage.class);
        verify(device, atLeastOnce()).sendMessage(deviceMessages.capture());
        assertTrue(deviceMessages.getAllValues().stream()
                .anyMatch(message -> message.getPayload().contains("\"type\":\"open\"")));
        assertTrue(deviceMessages.getAllValues().stream()
                .anyMatch(message -> message.getPayload().contains("uname -a")));

        ArgumentCaptor<TextMessage> adminMessages = ArgumentCaptor.forClass(TextMessage.class);
        verify(admin, atLeastOnce()).sendMessage(adminMessages.capture());
        assertTrue(adminMessages.getAllValues().stream()
                .anyMatch(message -> message.getPayload().contains("Linux orin")));
    }

    @Test
    void secondAdministratorCannotTakeOverActiveTerminal() throws Exception {
        handler.afterConnectionEstablished(device);
        handler.afterConnectionEstablished(admin);

        handler.afterConnectionEstablished(secondAdmin);

        verify(secondAdmin).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void invalidAdminMessageIsNotForwarded() throws Exception {
        handler.afterConnectionEstablished(device);
        handler.afterConnectionEstablished(admin);

        handler.handleTextMessage(admin, new TextMessage("{\"type\":\"output\",\"data\":\"forged\"}"));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(device, atLeastOnce()).sendMessage(messages.capture());
        assertTrue(messages.getAllValues().stream().noneMatch(message -> message.getPayload().contains("forged")));
        verify(device, never()).close(CloseStatus.BAD_DATA);
    }

    private void configure(WebSocketSession session, String role, String id) {
        lenient().when(session.getAttributes()).thenReturn(Map.of("terminalRole", role, "deviceSn", SN));
        lenient().when(session.getId()).thenReturn(id);
        lenient().when(session.isOpen()).thenReturn(true);
    }
}
