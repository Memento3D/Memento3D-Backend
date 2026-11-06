package com.memento3d.customization.infrastructure.persistence.repository;
import com.memento3d.customization.domain.model.CustomizationDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CustomizationDraftRepository extends JpaRepository<CustomizationDraft,UUID> {
    Optional<CustomizationDraft> findByModelOutputId(UUID modelOutputId);
}
