package com.memento3d.identity.infrastructure.persistence.repository;

import com.memento3d.identity.domain.model.RefreshToken;
import com.memento3d.identity.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserAndRevokedFalse(User user);

    void deleteByExpiresAtBefore(Instant now);

    void deleteByUser(User user);
}
