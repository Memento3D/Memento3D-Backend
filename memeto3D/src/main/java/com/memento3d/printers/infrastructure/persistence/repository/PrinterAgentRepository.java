package com.memento3d.printers.infrastructure.persistence.repository;

import com.memento3d.printers.domain.model.PrinterAgent;
import com.memento3d.printers.domain.model.PrinterAgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrinterAgentRepository extends JpaRepository<PrinterAgent, UUID> {

    Optional<PrinterAgent> findByTokenHash(String tokenHash);

    boolean existsByNameIgnoreCase(String name);

    List<PrinterAgent> findByStatusAndLastHeartbeatAtBefore(PrinterAgentStatus status, Instant threshold);
}
