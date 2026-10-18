package com.memento3d.printjob.infrastructure.persistence.repository;

import com.memento3d.printjob.domain.model.PrintJobConsumption;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintJobConsumptionRepository extends JpaRepository<PrintJobConsumption, UUID> {

    @EntityGraph(attributePaths = {"printJob", "warehouse", "material"})
    Optional<PrintJobConsumption> findByPrintJobId(UUID printJobId);
}
