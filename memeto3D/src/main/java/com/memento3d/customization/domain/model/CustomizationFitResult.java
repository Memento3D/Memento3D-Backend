package com.memento3d.customization.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="customization_fit_results",uniqueConstraints=@UniqueConstraint(name="uq_custom_fit_job_attempt",columnNames={"job_id","attempt_id"}))
@Getter @Setter @NoArgsConstructor
public class CustomizationFitResult {
    @Id private UUID id;
    @Column(name="job_id",nullable=false,updatable=false) private UUID jobId;
    @Column(name="attempt_id",nullable=false,updatable=false) private UUID attemptId;
    @Column(name="user_id",nullable=false,updatable=false) private UUID userId;
    @Column(name="scan_id",nullable=false,updatable=false) private UUID scanId;
    @Column(name="catalog_id",nullable=false,updatable=false) private UUID catalogId;
    @Column(nullable=false,updatable=false,length=100) private String policy;
    @Column(name="base_sha256",nullable=false,updatable=false,length=64) private String baseSha256;
    @Column(name="manifest_sha256",nullable=false,updatable=false,length=64) private String manifestSha256;
    @Column(name="profile_sha256",nullable=false,updatable=false,length=64) private String profileSha256;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="scene_json",nullable=false,updatable=false,columnDefinition="jsonb") private String sceneJson;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
}
