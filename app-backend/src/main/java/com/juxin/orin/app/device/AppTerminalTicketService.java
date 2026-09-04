package com.juxin.orin.app.device;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppTerminalTicketService {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String issue(String sn) {
        cleanup();
        byte[] value = new byte[24];
        random.nextBytes(value);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        tickets.put(ticket, new Ticket(sn, Instant.now().plusSeconds(120)));
        return ticket;
    }

    public boolean consume(String ticket, String sn) {
        if (ticket == null || sn == null) return false;
        Ticket value = tickets.remove(ticket);
        return value != null && value.expiresAt().isAfter(Instant.now()) && value.sn().equals(sn);
    }

    private void cleanup() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record Ticket(String sn, Instant expiresAt) {}
}
