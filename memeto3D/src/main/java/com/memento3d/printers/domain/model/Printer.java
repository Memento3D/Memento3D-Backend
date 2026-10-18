package com.memento3d.printers.domain.model;

import com.memento3d.printers.domain.exception.PrinterConflictException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Physical printer. Holds only slow-changing master data plus the last known coarse status.
 * High-frequency telemetry (temperatures, progress) is intentionally NOT stored here
 * (see Phase 8: Redis current-state + optional history table).
 * <p>
 * Status rules live in this class so that every code path (heartbeat, scheduler, manager action,
 * future PrintJob start) goes through the same checks.
 */
@Entity
@Table(
        name = "printers",
        indexes = {
                @Index(name = "idx_printers_status", columnList = "status"),
                @Index(name = "idx_printers_agent", columnList = "agent_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Printer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    @Builder.Default
    private PrinterProvider provider = PrinterProvider.OCTOPRINT;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PrinterStatus status = PrinterStatus.OFFLINE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private PrinterAgent agent;

    /** Identifier of this printer on the agent side (OctoPrint instance/profile id). */
    @Column(name = "octoprint_printer_id", length = 100)
    private String octoprintPrinterId;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ------------------------------------------------------------------ domain behaviour

    /**
     * Applies a state reported by the agent. MAINTENANCE is manager-owned and is never overwritten
     * by agents (only {@code lastSeenAt} is refreshed).
     */
    public void applyAgentReport(PrinterStatus reported, Instant now) {
        if (reported == null || !reported.isAgentReportable()) {
            throw new IllegalArgumentException("Agents cannot report status " + reported);
        }
        this.lastSeenAt = now;
        if (this.status != PrinterStatus.MAINTENANCE) {
            this.status = reported;
        }
    }

    /** Called when the owning agent stops heartbeating. Maintenance is preserved. */
    public void markOffline() {
        if (this.status != PrinterStatus.MAINTENANCE) {
            this.status = PrinterStatus.OFFLINE;
        }
    }

    public void enterMaintenance() {
        if (status == PrinterStatus.MAINTENANCE) {
            throw new PrinterConflictException("INVALID_PRINTER_STATE", "Printer is already in maintenance");
        }
        if (status.isBusy()) {
            throw new PrinterConflictException("INVALID_PRINTER_STATE",
                    "Printer cannot enter maintenance while status is " + status);
        }
        this.status = PrinterStatus.MAINTENANCE;
    }

    /** Leaves maintenance; real status is re-learned from the next agent heartbeat. */
    public void exitMaintenance() {
        if (status != PrinterStatus.MAINTENANCE) {
            throw new PrinterConflictException("INVALID_PRINTER_STATE",
                    "Printer is not in maintenance (status " + status + ")");
        }
        this.status = PrinterStatus.OFFLINE;
    }

    /** Used by PrintJob start validation (Phase 6/7). */
    public boolean isAvailableForPrint() {
        return status == PrinterStatus.IDLE;
    }
}
