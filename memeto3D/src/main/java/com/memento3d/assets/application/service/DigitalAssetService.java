package com.memento3d.assets.application.service;

import com.memento3d.assets.api.response.DigitalAssetResponse;
import com.memento3d.assets.api.response.DownloadUrlResponse;
import com.memento3d.assets.domain.model.DigitalAsset;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;

import java.util.List;
import java.util.UUID;

public interface DigitalAssetService {
    List<DigitalAssetResponse> getMyDigitalAssets(UUID userId);
    DigitalAssetResponse getDigitalAssetById(UUID assetId, UUID currentUserId, Role currentUserRole);
    DownloadUrlResponse generateDownloadUrl(UUID assetId, UUID currentUserId, Role currentUserRole);
    DigitalAsset createOrUpdateAsset(UUID userId, UUID orderItemId, ReconstructionJob job, String format, String path, String url, Long size, String sha256);
}
