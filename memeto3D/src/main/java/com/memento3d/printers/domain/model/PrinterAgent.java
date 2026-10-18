package com.memento3d.printers.domain.model;

import com.memento3d.printers.domain.exception.AgentUnauthorizedException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A Local Printer Agent (Raspberry Pi / PC next to OctoPrint). It authenticates to the backend
 * with a long random token; only the SHA-256 hash of the token is stored.
 * The OctoPrint API key never leaves the agent host.
 */
@Entity
@Table(name = "printer_agents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrinterAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PrinterAgentStatus status = PrinterAgentStatus.OFFLINE;

    @Column(name = "agent_version", length = 50)
    private String agentVersion;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "token_rotated_at")
    private Instant tokenRotatedAt;

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

    public boolean isRevoked() {
        return status == PrinterAgentStatus.REVOKED;
    }

    public void recordHeartbeat(String reportedVersion, Instant now) {
        if (isRevoked()) {
            throw new AgentUnauthorizedException();
        }
        this.status = PrinterAgentStatus.ONLINE;
        this.lastHeartbeatAt = now;
        if (reportedVersion != null && !reportedVersion.isBlank()) {
            this.agentVersion = reportedVersion;
        }
    }

    public void markOffline() {
        if (status == PrinterAgentStatus.ONLINE) {
            this.status = PrinterAgentStatus.OFFLINE;
        }
    }

    public void revoke() {
        this.status = PrinterAgentStatus.REVOKED;
    }

    /** Replaces the credential. A rotated agent must heartbeat again to become ONLINE. */
    public void rotateToken(String newTokenHash, Instant now) {
        this.tokenHash = newTokenHash;
        this.tokenRotatedAt = now;
        if (status == PrinterAgentStatus.ONLINE) {
            this.status = PrinterAgentStatus.OFFLINE;
        }
    }
}
