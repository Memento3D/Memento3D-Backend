package com.memento3d.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.ROLE_USER;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    /**
     * JWT revocation marker: access tokens issued before this instant are rejected.
     * Updated whenever the password is reset or changed.
     */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "storage_used_bytes", nullable = false)
    @Builder.Default
    private Long storageUsedBytes = 0L;

    @org.hibernate.annotations.ColumnDefault("10737418240")
    @Column(name = "storage_quota_bytes", nullable = false)
    @Builder.Default
    private Long storageQuotaBytes = 10737418240L; // 10 GB default

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
