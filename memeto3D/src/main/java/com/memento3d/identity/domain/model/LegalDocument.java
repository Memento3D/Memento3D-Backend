package com.memento3d.identity.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "legal_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uq_legal_documents_type_version", columnNames = {"type", "version"})
})
@Getter
@Setter
@Builder
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 50)
    private String type; // e.g. "TERMS", "PRIVACY"

    @Column(name = "version", nullable = false, length = 20)
    private String version; // e.g. "1.0"

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LegalDocument() {
    }

    public LegalDocument(UUID id, String type, String version, String title, String content, Instant effectiveAt, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.type = type;
        this.version = version;
        this.title = title;
        this.content = content;
        this.effectiveAt = effectiveAt;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
