package com.juxin.orin.websocket;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Component
public class TerminalTicketService {

    private static final long TICKET_TTL_MS = 30_000L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, TicketRecord> tickets = new ConcurrentHashMap<>();
    private final LongSupplier now;

    public TerminalTicketService() {
        this(System::currentTimeMillis);
    }

    TerminalTicketService(LongSupplier now) {
        this.now = now;
    }

    public IssuedTicket issue(Long adminId, String deviceSn) {
        if (adminId == null || deviceSn == null || deviceSn.isBlank()) {
            throw new IllegalArgumentException("管理员和设备不能为空");
        }
        purgeExpired();
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        long expiresAt = now.getAsLong() + TICKET_TTL_MS;
        tickets.put(ticket, new TicketRecord(adminId, deviceSn, expiresAt));
        return new IssuedTicket(ticket, expiresAt);
    }

    public boolean consume(String ticket, String deviceSn) {
        if (ticket == null || ticket.isBlank() || deviceSn == null) {
            return false;
        }
        TicketRecord record = tickets.remove(ticket);
        return record != null
                && record.expiresAtEpochMs() >= now.getAsLong()
                && record.deviceSn().equals(deviceSn);
    }

    private void purgeExpired() {
        long current = now.getAsLong();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMs() < current);
    }

    public record IssuedTicket(String ticket, long expiresAtEpochMs) {
    }

    private record TicketRecord(Long adminId, String deviceSn, long expiresAtEpochMs) {
    }
}
