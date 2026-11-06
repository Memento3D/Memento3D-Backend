package com.memento3d.customization.application.dto;

import java.util.List;
public record CatalogImportManifest(String code,String bodyPreset,String bucket,String groupsJson,
        String defaultSelectionJson,List<ObjectPin> files,List<Asset> assets) {
    public record ObjectPin(String key,long bytes,String sha256) {}
    public record Asset(String code,String version,String name,String kind,String slot,String objectKey,
            long bytes,String sha256,String cloudinaryPublicId,long cloudinaryVersion,String thumbnailUrl,
            String thumbnailSha256,String placementPolicy,String compatibilityJson,boolean printReady) {}
}
