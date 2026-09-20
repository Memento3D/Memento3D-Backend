package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.Scan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanRepository extends JpaRepository<Scan, UUID> {

    List<Scan> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<Scan> findByIdAndDeletedAtIsNull(UUID id);
}
