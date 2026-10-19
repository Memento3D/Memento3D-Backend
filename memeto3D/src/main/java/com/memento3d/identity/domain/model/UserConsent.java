package com.memento3d.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_consents")
@Getter
@Setter
@Builder
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType; // "TERMS", "PRIVACY"

    @Column(name = "document_version", nullable = false, length = 20)
    private String documentVersion; // "1.0"

    @Column(name = "consent_type", nullable = false, length = 50)
    private String consentType; // "TERMS", "PRIVACY", "MARKETING"

    @Column(name = "accepted", nullable = false)
    @Builder.Default
    private boolean accepted = true;

    @CreationTimestamp
    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "consent_source", nullable = false, length = 50)
    private String consentSource; // "WEB_REGISTER", "GOOGLE_ONBOARDING", "SETTINGS"

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    public UserConsent() {
    }

    public UserConsent(UUID id, User user, UUID documentId, String documentType, String documentVersion, String consentType, boolean accepted, Instant acceptedAt, String consentSource, String ipAddress, String userAgent) {
        this.id = id;
        this.user = user;
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentVersion = documentVersion;
        this.consentType = consentType;
        this.accepted = accepted;
        this.acceptedAt = acceptedAt;
        this.consentSource = consentSource;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
