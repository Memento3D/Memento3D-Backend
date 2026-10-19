package com.memento3d.shared.outbox.infrastructure.persistence.repository;

import com.memento3d.shared.outbox.domain.model.TransactionalOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionalOutboxRepository extends JpaRepository<TransactionalOutbox, UUID> {
    List<TransactionalOutbox> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
