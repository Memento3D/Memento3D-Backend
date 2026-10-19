package com.memento3d.identity.infrastructure.persistence.repository;

import com.memento3d.identity.domain.model.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    Optional<LegalDocument> findFirstByTypeAndActiveTrueOrderByEffectiveAtDesc(String type);

    Optional<LegalDocument> findByTypeAndVersion(String type, String version);
}
