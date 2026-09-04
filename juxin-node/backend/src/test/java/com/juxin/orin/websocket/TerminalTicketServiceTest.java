package com.juxin.orin.websocket;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalTicketServiceTest {

    private static final String SN = "ORIN-0123456789AB";

    @Test
    void ticketCanOnlyBeConsumedOnceForItsDevice() {
        AtomicLong now = new AtomicLong(1_000L);
        TerminalTicketService service = new TerminalTicketService(now::get);
        TerminalTicketService.IssuedTicket issued = service.issue(7L, SN);

        assertFalse(service.consume(issued.ticket(), "ORIN-OTHER"));
        assertFalse(service.consume(issued.ticket(), SN));

        issued = service.issue(7L, SN);
        assertTrue(service.consume(issued.ticket(), SN));
        assertFalse(service.consume(issued.ticket(), SN));
    }

    @Test
    void expiredTicketIsRejected() {
        AtomicLong now = new AtomicLong(1_000L);
        TerminalTicketService service = new TerminalTicketService(now::get);
        TerminalTicketService.IssuedTicket issued = service.issue(7L, SN);

        now.set(issued.expiresAtEpochMs() + 1);

        assertFalse(service.consume(issued.ticket(), SN));
    }
}
