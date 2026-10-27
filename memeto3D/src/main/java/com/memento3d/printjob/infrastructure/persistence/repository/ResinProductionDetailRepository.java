package com.memento3d.printjob.infrastructure.persistence.repository;

import com.memento3d.printjob.domain.model.ResinProductionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResinProductionDetailRepository extends JpaRepository<ResinProductionDetail, UUID> {

    Optional<ResinProductionDetail> findByPrintJobId(UUID printJobId);

    List<ResinProductionDetail> findByOrderId(UUID orderId);

    Optional<ResinProductionDetail> findByOrderItemId(UUID orderItemId);
}
