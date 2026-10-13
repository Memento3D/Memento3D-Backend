package com.memento3d.assets.infrastructure.persistence.repository;

import com.memento3d.assets.domain.model.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
}
