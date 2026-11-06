package com.memento3d.customization.api.response;
import java.time.Instant;
import java.util.*;

public record ModelCustomizationSceneResponse(UUID modelOutputId,UUID jobId,UUID attemptId,UUID fitResultId,
    String catalogCode,String bodyPreset,String qualityStatus,boolean readyForMachine,String coordinateSystem,
    BaseModel baseModel,List<Group> groups,List<Asset> assets,Map<String,UUID> defaultSelection,
    CustomizationDraftResponse customization,long urlTtlSeconds,Instant urlsExpireAt) {
    public record BaseModel(String url,String sha256,long fileSizeBytes) {}
    public record Group(String id,boolean required,boolean exclusive,boolean noneAllowed,List<UUID> assetVersionIds) {}
    public record Asset(UUID assetVersionId,String code,String version,String name,String kind,String slot,
        String geometryMode,String url,String sha256,String sourceSha256,long fileSizeBytes,
        String thumbnailUrl,List<Double> matrixColumnMajor) {}
}
