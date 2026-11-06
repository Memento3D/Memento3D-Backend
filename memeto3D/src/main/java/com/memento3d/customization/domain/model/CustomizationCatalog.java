package com.memento3d.customization.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="customization_catalogs")
@Getter @Setter @NoArgsConstructor
public class CustomizationCatalog {
    @Id private UUID id;
    @Column(nullable=false,unique=true,length=100) private String code;
    @Column(name="body_preset",nullable=false,length=100) private String bodyPreset;
    @Column(nullable=false,length=20) private String status;
    @Column(name="import_sha256",length=64) private String importSha256;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="groups_json",nullable=false,columnDefinition="jsonb") private String groupsJson="[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="default_selection_json",nullable=false,columnDefinition="jsonb") private String defaultSelectionJson="{}";
    @Column(name="verified_object_count",nullable=false) private int verifiedObjectCount;
    @Column(name="verified_at") private Instant verifiedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
}
