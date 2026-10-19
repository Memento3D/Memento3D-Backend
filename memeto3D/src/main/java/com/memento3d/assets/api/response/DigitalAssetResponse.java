package com.memento3d.assets.api.response;

import com.memento3d.assets.domain.model.DigitalAsset;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class DigitalAssetResponse {
    private UUID id;
    private UUID userId;
    private UUID orderItemId;
    private UUID jobId;
    private String fileFormat;
    private String storageProvider;
    private String deliveryUrl;
    private Long fileSizeBytes;
    private String sha256;
    private String status;
    private Integer downloadCount;
    private Instant createdAt;

    public static DigitalAssetResponse fromEntity(DigitalAsset asset) {
        return DigitalAssetResponse.builder()
                .id(asset.getId())
                .userId(asset.getUserId())
                .orderItemId(asset.getOrderItemId())
                .jobId(asset.getJob() != null ? asset.getJob().getId() : null)
                .fileFormat(asset.getFileFormat())
                .storageProvider(asset.getStorageProvider())
                .deliveryUrl(asset.getDeliveryUrl())
                .fileSizeBytes(asset.getFileSizeBytes())
                .sha256(asset.getSha256())
                .status(asset.getStatus())
                .downloadCount(asset.getDownloadCount())
                .createdAt(asset.getCreatedAt())
                .build();
    }
}
