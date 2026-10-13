package com.memento3d.identity.infrastructure.persistence.repository;

import com.memento3d.identity.domain.model.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, UUID> {
    boolean existsByToken(String token);

    @Modifying
    @Query("DELETE FROM BlacklistedToken b WHERE b.expiryDate < :now")
    void deleteExpiredTokens(@Param("now") Instant now);
}
