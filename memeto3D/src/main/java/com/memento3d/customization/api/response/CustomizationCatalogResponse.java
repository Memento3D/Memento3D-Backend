package com.memento3d.customization.api.response;

import java.util.*;
public record CustomizationCatalogResponse(String code,String bodyPreset,String status,List<Map<String,Object>> groups,
        Map<String,Object> defaultSelection,List<Asset> assets,long urlTtlSeconds) {
    public record Asset(UUID id,String code,String version,String name,String kind,String slot,
            String previewUrl,long fileSizeBytes,String sha256,String thumbnailUrl,
            String placementPolicy,Map<String,Object> compatibility,boolean printReady) {}
}
