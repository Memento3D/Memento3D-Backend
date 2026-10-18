package com.memento3d.printers.infrastructure.persistence.repository;

import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.model.PrinterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrinterRepository extends JpaRepository<Printer, UUID> {

    @Override
    @EntityGraph(attributePaths = "agent")
    Page<Printer> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "agent")
    Page<Printer> findByStatus(PrinterStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "agent")
    Optional<Printer> findWithAgentById(UUID id);

    /**
     * Row lock for command flows (start/pause/cancel in Phase 6/7) and maintenance toggles,
     * so two concurrent requests cannot both pass the "printer is IDLE" check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Printer p left join fetch p.agent where p.id = :id")
    Optional<Printer> findByIdForUpdate(@Param("id") UUID id);

    List<Printer> findByAgentId(UUID agentId);

    Optional<Printer> findByAgentIdAndOctoprintPrinterId(UUID agentId, String octoprintPrinterId);

    boolean existsBySerialNumberIgnoreCase(String serialNumber);

    boolean existsByAgentIdAndOctoprintPrinterId(UUID agentId, String octoprintPrinterId);
}
