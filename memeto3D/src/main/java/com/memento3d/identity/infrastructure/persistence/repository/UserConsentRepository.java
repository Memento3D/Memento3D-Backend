package com.memento3d.identity.infrastructure.persistence.repository;

import com.memento3d.identity.domain.model.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    List<UserConsent> findByUserId(UUID userId);

    Optional<UserConsent> findTopByUserIdAndConsentTypeOrderByAcceptedAtDesc(UUID userId, String consentType);

    boolean existsByUserIdAndConsentTypeAndDocumentVersionAndAcceptedTrue(UUID userId, String consentType, String documentVersion);
}
