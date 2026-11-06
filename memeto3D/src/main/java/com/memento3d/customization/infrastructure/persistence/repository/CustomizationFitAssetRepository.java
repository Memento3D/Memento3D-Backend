package com.memento3d.customization.infrastructure.persistence.repository;
import com.memento3d.customization.domain.model.CustomizationFitAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CustomizationFitAssetRepository extends JpaRepository<CustomizationFitAsset,UUID> {
    List<CustomizationFitAsset> findByFitResultId(UUID fitResultId);
}
