package com.memento3d.identity.infrastructure.persistence.repository;

import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    List<UserIdentity> findByUserId(UUID userId);

    boolean existsByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
