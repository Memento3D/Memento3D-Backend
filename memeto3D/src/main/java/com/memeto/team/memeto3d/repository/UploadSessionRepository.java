package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
}
