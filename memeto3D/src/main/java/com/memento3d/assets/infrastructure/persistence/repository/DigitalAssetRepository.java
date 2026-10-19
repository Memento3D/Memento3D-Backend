package com.memento3d.assets.infrastructure.persistence.repository;

import com.memento3d.assets.domain.model.DigitalAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, UUID> {
    List<DigitalAsset> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<DigitalAsset> findByJob_Id(UUID jobId);
    Optional<DigitalAsset> findByOrderItemId(UUID orderItemId);
}
