package com.memento3d.shared.event.infrastructure.persistence.repository;

import com.memento3d.shared.event.domain.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
