package com.memento3d.assets.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ScanVideoUploadResponse {
    private UUID id;
    
    @JsonProperty("scan_id")
    private UUID scanId;
    
    @JsonProperty("original_filename")
    private String originalFilename;
    
    @JsonProperty("storage_path")
    private String storagePath;
    
    @JsonProperty("file_size_bytes")
    private Long fileSizeBytes;
    
    @JsonProperty("mime_type")
    private String mimeType;
    
    private String format;
    
    @JsonProperty("duration_seconds")
    private java.math.BigDecimal durationSeconds;
    
    private java.math.BigDecimal fps;
    private Integer width;
    private Integer height;
    
    @JsonProperty("created_at")
    private java.time.Instant createdAt;
}
