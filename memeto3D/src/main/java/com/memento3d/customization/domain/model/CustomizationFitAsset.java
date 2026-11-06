package com.memento3d.customization.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity @Table(name="customization_fit_assets",uniqueConstraints=@UniqueConstraint(name="uq_custom_fit_asset",columnNames={"fit_result_id","asset_version_id"}))
@Getter @Setter @NoArgsConstructor
public class CustomizationFitAsset {
    @Id private UUID id;
    @Column(name="fit_result_id",nullable=false,updatable=false) private UUID fitResultId;
    @Column(name="asset_version_id",nullable=false,updatable=false) private UUID assetVersionId;
    @Column(name="geometry_mode",nullable=false,updatable=false,length=30) private String geometryMode;
    @Column(name="storage_provider",nullable=false,updatable=false,length=30) private String storageProvider;
    @Column(name="object_key",nullable=false,updatable=false,length=500) private String objectKey;
    @Column(nullable=false,updatable=false,columnDefinition="text") private String location;
    @Column(name="file_size_bytes",nullable=false,updatable=false) private long fileSizeBytes;
    @Column(nullable=false,updatable=false,length=64) private String sha256;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="matrix_json",nullable=false,updatable=false,columnDefinition="jsonb") private String matrixJson;
}
