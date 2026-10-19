package com.memento3d.quotation.infrastructure.persistence.repository;

import com.memento3d.quotation.domain.model.ManualQuotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ManualQuotationRepository extends JpaRepository<ManualQuotation, UUID> {
    List<ManualQuotation> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<ManualQuotation> findByStatusOrderByCreatedAtDesc(String status);
}
