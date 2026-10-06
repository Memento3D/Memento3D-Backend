package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.ModelRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelRatingRepository extends JpaRepository<ModelRating, UUID> {

    Optional<ModelRating> findByScanIdAndUserId(UUID scanId, UUID userId);

    Optional<ModelRating> findByScanIdAndUserIdIsNull(UUID scanId);
}
