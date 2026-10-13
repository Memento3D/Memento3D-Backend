package com.memento3d.models.infrastructure.persistence.repository;

import com.memento3d.models.domain.model.ModelRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelRatingRepository extends JpaRepository<ModelRating, UUID> {

    Optional<ModelRating> findByScanIdAndUserId(UUID scanId, UUID userId);

    Optional<ModelRating> findByScanIdAndUserIdIsNull(UUID scanId);
}
