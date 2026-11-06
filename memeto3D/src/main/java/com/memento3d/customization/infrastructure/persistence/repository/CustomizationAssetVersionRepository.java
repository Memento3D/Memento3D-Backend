package com.memento3d.customization.infrastructure.persistence.repository;

import com.memento3d.customization.domain.model.CustomizationAssetVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CustomizationAssetVersionRepository extends JpaRepository<CustomizationAssetVersion,UUID> {
    Optional<CustomizationAssetVersion> findByAssetCodeAndVersion(String assetCode,String version);
}
