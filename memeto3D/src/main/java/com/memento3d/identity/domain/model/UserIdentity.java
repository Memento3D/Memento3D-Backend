package com.memento3d.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_identities_provider_subject", columnNames = {"provider", "provider_subject"})
})
@Getter
@Setter
@Builder
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    public UserIdentity() {
    }

    public UserIdentity(UUID id, User user, AuthProvider provider, String providerSubject, Instant linkedAt) {
        this.id = id;
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.linkedAt = linkedAt;
    }
}
