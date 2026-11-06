package com.memento3d.assets.application.service.impl;

import com.memento3d.assets.api.response.DigitalAssetResponse;
import com.memento3d.assets.api.response.DownloadUrlResponse;
import com.memento3d.assets.application.service.DigitalAssetService;
import com.memento3d.assets.domain.model.DigitalAsset;
import com.memento3d.assets.infrastructure.persistence.repository.DigitalAssetRepository;
import com.memento3d.entitlement.application.service.EntitlementService;
import com.memento3d.entitlement.domain.model.EntitlementType;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DigitalAssetServiceImpl implements DigitalAssetService {

    private final DigitalAssetRepository digitalAssetRepository;
    private final EntitlementService entitlementService;
    private final com.memento3d.models.application.service.ModelDeliveryService modelDelivery;

    @Override
    @Transactional(readOnly = true)
    public List<DigitalAssetResponse> getMyDigitalAssets(UUID userId) {
        return digitalAssetRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(DigitalAssetResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DigitalAssetResponse getDigitalAssetById(UUID assetId, UUID currentUserId, Role currentUserRole) {
        DigitalAsset asset = digitalAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Digital asset not found with ID: " + assetId));

        validateAssetAccess(asset, currentUserId, currentUserRole);
        return DigitalAssetResponse.fromEntity(asset);
    }

    @Override
    @Transactional
    public DownloadUrlResponse generateDownloadUrl(UUID assetId, UUID currentUserId, Role currentUserRole) {
        DigitalAsset asset = digitalAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Digital asset not found with ID: " + assetId));

        validateAssetAccess(asset, currentUserId, currentUserRole);

        // Enforce Entitlement verification for regular users
        boolean isStaff = (currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_MANAGER);
        if (!isStaff) {
            UUID resourceId = asset.getJob() != null && asset.getJob().getScan() != null
                    ? asset.getJob().getScan().getId()
                    : asset.getId();

            boolean hasEntitlement = entitlementService.hasEntitlement(currentUserId, resourceId, EntitlementType.DOWNLOAD_DIGITAL_MODEL)
                    || (asset.getJob() != null && entitlementService.hasEntitlement(currentUserId, asset.getJob().getId(), EntitlementType.DOWNLOAD_DIGITAL_MODEL));

            if (!hasEntitlement) {
                log.warn("User {} denied download URL for asset {}: Missing DOWNLOAD_DIGITAL_MODEL entitlement", currentUserId, assetId);
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_ENTITLEMENT", "You do not have entitlement to download high quality digital 3D models.");
            }
        }

        // Increment download audit counter
        asset.setDownloadCount(asset.getDownloadCount() + 1);
        digitalAssetRepository.save(asset);

        Instant expiresAt = "b2".equalsIgnoreCase(asset.getStorageProvider())
                ? Instant.now().plusSeconds(modelDelivery.urlTtlSeconds())
                : Instant.now().plus(60, ChronoUnit.MINUTES);
        // Signed or delivery URL with temporal token
        String signedUrl = "b2".equalsIgnoreCase(asset.getStorageProvider())
                ? modelDelivery.url(asset.getStorageProvider(), asset.getStoragePath(), asset.getDeliveryUrl())
                : asset.getDeliveryUrl() + (asset.getDeliveryUrl().contains("?") ? "&" : "?") + "expires=" + expiresAt.getEpochSecond();

        log.info("Generated signed download URL for asset {} by user {}", assetId, currentUserId);
        return DownloadUrlResponse.builder()
                .assetId(asset.getId())
                .downloadUrl(signedUrl)
                .expiresAt(expiresAt)
                .fileFormat(asset.getFileFormat())
                .fileSizeBytes(asset.getFileSizeBytes())
                .build();
    }

    @Override
    @Transactional
    public DigitalAsset createOrUpdateAsset(
            UUID userId,
            UUID orderItemId,
            ReconstructionJob job,
            String format,
            String path,
            String url,
            Long size,
            String sha256
    ) {
        DigitalAsset asset = DigitalAsset.builder()
                .userId(userId)
                .orderItemId(orderItemId)
                .job(job)
                .fileFormat(format != null ? format.toUpperCase() : "GLB")
                .storageProvider("CLOUDINARY")
                .storagePath(path)
                .deliveryUrl(url)
                .fileSizeBytes(size != null ? size : 0L)
                .sha256(sha256)
                .status("READY")
                .downloadCount(0)
                .build();

        return digitalAssetRepository.save(asset);
    }

    private void validateAssetAccess(DigitalAsset asset, UUID currentUserId, Role currentUserRole) {
        boolean isStaff = (currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_MANAGER);
        if (!isStaff && !asset.getUserId().equals(currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_ASSET_ACCESS", "You cannot access another user's digital asset.");
        }
    }
}
