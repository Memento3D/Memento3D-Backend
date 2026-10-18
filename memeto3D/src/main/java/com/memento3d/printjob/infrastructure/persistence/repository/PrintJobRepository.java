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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    Optional<PrintJob> findByJobNumber(String jobNumber);

    @EntityGraph(attributePaths = {"printer", "material"})
    @Query("SELECT j FROM PrintJob j WHERE " +
            "(:status IS NULL OR j.status = :status) AND " +
            "(:printerId IS NULL OR j.printer.id = :printerId) AND " +
            "(:materialId IS NULL OR j.material.id = :materialId) " +
            "ORDER BY j.createdAt DESC")
    Page<PrintJob> findAllWithFilters(
            @Param("status") PrintJobStatus status,
            @Param("printerId") UUID printerId,
            @Param("materialId") UUID materialId,
            Pageable pageable
    );
}
