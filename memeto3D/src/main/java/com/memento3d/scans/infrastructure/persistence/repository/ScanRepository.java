package com.memento3d.scans.infrastructure.persistence.repository;

import com.memento3d.identity.domain.model.User;

import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanRepository extends JpaRepository<Scan, UUID> {

    List<Scan> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<Scan> findByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = {"user"})
    Page<Scan> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<Scan> findByUserIdAndStatusAndDeletedAtIsNull(UUID userId, ScanStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM Scan s
        WHERE s.id = :scanId
          AND s.deletedAt IS NULL
        """)
    Optional<Scan> findByIdForUpdate(
            @Param("scanId") UUID scanId
    );
}
