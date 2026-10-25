package com.memento3d.operations.reviewqueue.infrastructure.persistence.repository;

import com.memento3d.operations.reviewqueue.domain.model.ManualReviewItem;
import com.memento3d.operations.reviewqueue.domain.model.ReviewPriority;
import com.memento3d.operations.reviewqueue.domain.model.ReviewStatus;
import com.memento3d.operations.reviewqueue.domain.model.ReviewType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManualReviewItemRepository extends JpaRepository<ManualReviewItem, UUID> {

    @Query("""
        SELECT r FROM ManualReviewItem r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:reviewType IS NULL OR r.reviewType = :reviewType)
          AND (:priority IS NULL OR r.priority = :priority)
        ORDER BY r.createdAt DESC
    """)
    Page<ManualReviewItem> searchReviews(
            @Param("status") ReviewStatus status,
            @Param("reviewType") ReviewType reviewType,
            @Param("priority") ReviewPriority priority,
            Pageable pageable
    );

    Optional<ManualReviewItem> findFirstByReferenceIdAndStatus(UUID referenceId, ReviewStatus status);

    long countByStatus(ReviewStatus status);
}
