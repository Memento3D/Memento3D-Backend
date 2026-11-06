package com.memento3d.customization.infrastructure.persistence.repository;
import com.memento3d.customization.domain.model.CustomizationFitResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CustomizationFitResultRepository extends JpaRepository<CustomizationFitResult,UUID> {
    Optional<CustomizationFitResult> findByJobIdAndAttemptId(UUID jobId,UUID attemptId);
}
