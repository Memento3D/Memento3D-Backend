package com.memento3d.printjob.infrastructure.persistence.repository;

import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {
    java.util.Optional<PrintJob> findFirstByOrderItemIdAndStatusNotOrderByCreatedAtDesc(UUID orderItemId,PrintJobStatus status);

    @EntityGraph(attributePaths = {"printer", "material"})
    Optional<PrintJob> findByJobNumber(String jobNumber);

    @EntityGraph(attributePaths = {"printer", "material"})
    @Query("SELECT j FROM PrintJob j WHERE j.id = :id")
    Optional<PrintJob> findWithDetailsById(@Param("id") UUID id);

    Optional<PrintJob> findByOrderItemId(UUID orderItemId);

    @EntityGraph(attributePaths = {"printer", "material"})
    List<PrintJob> findByOrderId(UUID orderId);

    @Query("SELECT j.printer.id, COUNT(j) FROM PrintJob j WHERE j.printer.id IN :printerIds AND j.status IN :statuses GROUP BY j.printer.id")
    List<Object[]> countActiveJobsByPrinterIdsAndStatuses(@Param("printerIds") java.util.Collection<UUID> printerIds, @Param("statuses") java.util.Collection<PrintJobStatus> statuses);

    boolean existsByOrderItemIdAndStatusNot(UUID orderItemId, PrintJobStatus status);

    @Override
    @EntityGraph(attributePaths = {"printer", "material"})
    Page<PrintJob> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"printer", "material"})
    @Query(
        value = """
            SELECT j
            FROM PrintJob j
            WHERE (:status IS NULL OR j.status = :status)
              AND (:printerId IS NULL OR j.printer.id = :printerId)
              AND (:materialId IS NULL OR j.material.id = :materialId)
            """,
        countQuery = """
            SELECT COUNT(j)
            FROM PrintJob j
            WHERE (:status IS NULL OR j.status = :status)
              AND (:printerId IS NULL OR j.printer.id = :printerId)
              AND (:materialId IS NULL OR j.material.id = :materialId)
            """
    )
    Page<PrintJob> findAllWithFilters(
            @Param("status") PrintJobStatus status,
            @Param("printerId") UUID printerId,
            @Param("materialId") UUID materialId,
            Pageable pageable
    );

    long countByStatus(PrintJobStatus status);

    long countByPrinterIdAndStatusIn(UUID printerId, java.util.Collection<PrintJobStatus> statuses);
}
