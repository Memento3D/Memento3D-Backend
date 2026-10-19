package com.memento3d.assets.api.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class DownloadUrlResponse {
    private UUID assetId;
    private String downloadUrl;
    private Instant expiresAt;
    private String fileFormat;
    private Long fileSizeBytes;
}
