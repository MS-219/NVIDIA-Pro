package com.juxin.orin.websocket;

import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IEdgeDeviceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalHandshakeInterceptorTest {

    private static final String SN = "ORIN-0123456789AB";

    @Mock
    private IEdgeDeviceAccessService edgeDeviceAccessService;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler handler;

    private TerminalTicketService ticketService;
    private TerminalHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ticketService = new TerminalTicketService();
        interceptor = new TerminalHandshakeInterceptor(ticketService, edgeDeviceAccessService);
    }

    @Test
    void adminHandshakeConsumesOneTimeTicket() {
        String ticket = ticketService.issue(7L, SN).ticket();
        when(request.getURI()).thenReturn(URI.create(
                "https://example.test/ws/admin/terminal/" + SN + "?ticket=" + ticket));
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(request, response, handler, attributes));
        assertEquals("admin", attributes.get("terminalRole"));
        assertEquals(SN, attributes.get("deviceSn"));
        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
    }

    @Test
    void deviceHandshakeRequiresTokenOwnedByPathSn() {
        Device device = new Device();
        device.setSn(SN);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Orin-Device-Token", "device-token");
        when(request.getURI()).thenReturn(URI.create("https://example.test/ws/device/" + SN));
        when(request.getHeaders()).thenReturn(headers);
        when(edgeDeviceAccessService.authenticate("device-token")).thenReturn(device);
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(request, response, handler, attributes));

        verify(edgeDeviceAccessService).requireOwnedSn(device, SN);
        assertEquals("device", attributes.get("terminalRole"));
        assertEquals(SN, attributes.get("deviceSn"));
    }

    @Test
    void missingDeviceTokenRejectsHandshake() {
        when(request.getURI()).thenReturn(URI.create("https://example.test/ws/device/" + SN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
