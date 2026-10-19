package com.memento3d.customization.infrastructure.persistence.repository;

import com.memento3d.customization.domain.model.CustomizationOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomizationOptionRepository extends JpaRepository<CustomizationOption, UUID> {
    List<CustomizationOption> findByIsActiveTrue();
    List<CustomizationOption> findByProductIdAndIsActiveTrue(UUID productId);
    List<CustomizationOption> findByCategoryIdAndIsActiveTrue(UUID categoryId);
    Optional<CustomizationOption> findByCode(String code);
}
