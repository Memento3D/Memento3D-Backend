package com.memento3d.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blacklisted_tokens", indexes = {
    @Index(name = "idx_blacklisted_token", columnList = "token")
})
@Getter
@Setter
@Builder
public class BlacklistedToken {

    public BlacklistedToken() {
    }

    public BlacklistedToken(UUID id, String token, Instant expiryDate, Instant revokedAt) {
        this.id = id;
        this.token = token;
        this.expiryDate = expiryDate;
        this.revokedAt = revokedAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token", nullable = false, length = 1024, unique = true)
    private String token;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @CreationTimestamp
    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt;
}
