package com.memento3d.reconstruction.infrastructure.persistence.repository;

import com.memento3d.reconstruction.domain.model.PreviewQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreviewQuotaRepository extends JpaRepository<PreviewQuota, UUID> {

    Optional<PreviewQuota> findByUser_Id(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM PreviewQuota q WHERE q.user.id = :userId")
    Optional<PreviewQuota> findByUserIdForUpdate(@Param("userId") UUID userId);
}
