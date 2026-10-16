package com.memento3d.printers.domain.model;

import com.memento3d.printers.domain.exception.PrinterConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PrinterTest {

    private Printer printer(PrinterStatus status) {
        return Printer.builder().name("P1").serialNumber("S1").model("Ender").status(status).build();
    }

    @Test
    void agentReport_updatesStatusAndLastSeen() {
        Printer p = printer(PrinterStatus.OFFLINE);
        Instant now = Instant.now();
        p.applyAgentReport(PrinterStatus.IDLE, now);
        assertEquals(PrinterStatus.IDLE, p.getStatus());
        assertEquals(now, p.getLastSeenAt());
        assertTrue(p.isAvailableForPrint());
    }

    @Test
    void agentReport_neverOverridesMaintenance() {
        Printer p = printer(PrinterStatus.MAINTENANCE);
        p.applyAgentReport(PrinterStatus.PRINTING, Instant.now());
        assertEquals(PrinterStatus.MAINTENANCE, p.getStatus());
        assertNotNull(p.getLastSeenAt());
    }

    @Test
    void agentReport_rejectsMaintenanceStatus() {
        Printer p = printer(PrinterStatus.IDLE);
        assertThrows(IllegalArgumentException.class,
                () -> p.applyAgentReport(PrinterStatus.MAINTENANCE, Instant.now()));
    }

    @Test
    void enterMaintenance_notAllowedWhileBusy() {
        for (PrinterStatus s : new PrinterStatus[]{PrinterStatus.PRINTING, PrinterStatus.PAUSED, PrinterStatus.PREPARING}) {
            Printer p = printer(s);
            assertThrows(PrinterConflictException.class, p::enterMaintenance);
        }
    }

    @Test
    void maintenanceRoundTrip() {
        Printer p = printer(PrinterStatus.IDLE);
        p.enterMaintenance();
        assertEquals(PrinterStatus.MAINTENANCE, p.getStatus());
        assertThrows(PrinterConflictException.class, p::enterMaintenance);
        p.exitMaintenance();
        assertEquals(PrinterStatus.OFFLINE, p.getStatus());
        assertThrows(PrinterConflictException.class, p::exitMaintenance);
    }

    @Test
    void markOffline_preservesMaintenance() {
        Printer p = printer(PrinterStatus.MAINTENANCE);
        p.markOffline();
        assertEquals(PrinterStatus.MAINTENANCE, p.getStatus());
        Printer q = printer(PrinterStatus.PRINTING);
        q.markOffline();
        assertEquals(PrinterStatus.OFFLINE, q.getStatus());
    }
}
