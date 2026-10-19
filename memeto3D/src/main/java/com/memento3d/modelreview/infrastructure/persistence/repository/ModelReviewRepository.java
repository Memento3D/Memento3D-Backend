package com.memento3d.modelreview.infrastructure.persistence.repository;

import com.memento3d.modelreview.domain.model.ModelReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModelReviewRepository extends JpaRepository<ModelReview, UUID> {
    List<ModelReview> findByModelVersion_IdOrderByCreatedAtDesc(UUID modelVersionId);
    long countByModelVersion_Scan_IdAndDecision(UUID scanId, String decision);
}
