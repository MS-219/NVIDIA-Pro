package com.juxin.orin.websocket;

import com.juxin.orin.entity.Device;
import com.juxin.orin.exception.EdgeDeviceApiException;
import com.juxin.orin.service.IEdgeDeviceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TerminalHandshakeInterceptor implements HandshakeInterceptor {

    static final String ROLE_ATTRIBUTE = "terminalRole";
    static final String SN_ATTRIBUTE = "deviceSn";

    private static final Pattern ADMIN_PATH = Pattern.compile("^/ws/admin/terminal/((?:ORIN|RK3588|JD)-[A-F0-9]{12,32})$");
    private static final Pattern DEVICE_PATH = Pattern.compile("^/ws/device/((?:ORIN|RK3588|JD)-[A-F0-9]{12,32})$");

    private final TerminalTicketService ticketService;
    private final IEdgeDeviceAccessService edgeDeviceAccessService;

    public TerminalHandshakeInterceptor(
            TerminalTicketService ticketService,
            IEdgeDeviceAccessService edgeDeviceAccessService) {
        this.ticketService = ticketService;
        this.edgeDeviceAccessService = edgeDeviceAccessService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        URI uri = request.getURI();
        Matcher adminMatcher = ADMIN_PATH.matcher(uri.getPath());
        if (adminMatcher.matches()) {
            String sn = adminMatcher.group(1);
            String ticket = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("ticket");
            if (!ticketService.consume(ticket, sn)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(ROLE_ATTRIBUTE, "admin");
            attributes.put(SN_ATTRIBUTE, sn);
            return true;
        }

        Matcher deviceMatcher = DEVICE_PATH.matcher(uri.getPath());
        if (deviceMatcher.matches()) {
            String sn = deviceMatcher.group(1);
            String token = request.getHeaders().getFirst(IEdgeDeviceAccessService.DEVICE_TOKEN_HEADER);
            if (token == null || token.isBlank()) {
                token = request.getHeaders().getFirst(IEdgeDeviceAccessService.RK3588_DEVICE_TOKEN_HEADER);
            }
            if (token == null || token.isBlank()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            try {
                Device device = edgeDeviceAccessService.authenticate(token);
                edgeDeviceAccessService.requireOwnedSn(device, sn);
            } catch (EdgeDeviceApiException exception) {
                response.setStatusCode(exception.getStatus());
                return false;
            } catch (RuntimeException exception) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(ROLE_ATTRIBUTE, "device");
            attributes.put(SN_ATTRIBUTE, sn);
            return true;
        }

        response.setStatusCode(HttpStatus.NOT_FOUND);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No resources are held by the handshake phase.
    }
}
