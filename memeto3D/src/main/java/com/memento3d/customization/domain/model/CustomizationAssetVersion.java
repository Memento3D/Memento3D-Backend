package com.memento3d.customization.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="customization_asset_versions",uniqueConstraints=@UniqueConstraint(name="uq_custom_asset_version",columnNames={"asset_code","version"}))
@Getter @Setter @NoArgsConstructor
public class CustomizationAssetVersion {
    @Id private UUID id;
    @Column(name="asset_code",nullable=false,updatable=false,length=100) private String assetCode;
    @Column(nullable=false,updatable=false,length=40) private String version;
    @Column(nullable=false,updatable=false) private String name;
    @Column(nullable=false,updatable=false,length=40) private String kind;
    @Column(nullable=false,updatable=false,length=40) private String slot;
    @Column(nullable=false,updatable=false,length=100) private String bucket;
    @Column(name="object_key",nullable=false,updatable=false,length=500) private String objectKey;
    @Column(name="file_size_bytes",nullable=false,updatable=false) private long fileSizeBytes;
    @Column(nullable=false,updatable=false,length=64) private String sha256;
    @Column(name="cloudinary_public_id",nullable=false,updatable=false,length=500) private String cloudinaryPublicId;
    @Column(name="cloudinary_version",nullable=false,updatable=false) private long cloudinaryVersion;
    @Column(name="thumbnail_url",nullable=false,updatable=false,columnDefinition="text") private String thumbnailUrl;
    @Column(name="thumbnail_sha256",nullable=false,updatable=false,length=64) private String thumbnailSha256;
    @Column(name="placement_policy",nullable=false,updatable=false,length=100) private String placementPolicy;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="compatibility_json",nullable=false,updatable=false,columnDefinition="jsonb") private String compatibilityJson;
    @Column(name="print_ready",nullable=false,updatable=false) private boolean printReady;
    @Column(name="metadata_sha256",nullable=false,updatable=false,length=64) private String metadataSha256;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
}
