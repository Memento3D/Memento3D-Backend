package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Details about the image that this image was identified as a duplicate of")
public class DuplicateImageInfo {

    @JsonProperty("image_id")
    @Schema(description = "ID of the duplicate image", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private UUID imageId;

    @JsonProperty("original_filename")
    @Schema(description = "Original filename of the duplicate image", example = "IMG_0001.JPG")
    private String originalFilename;

    @JsonProperty("thumbnail_path")
    @Schema(description = "Thumbnail path of the duplicate image", example = "https://res.cloudinary.com/demo/image/upload/v1234/thumb1.jpg")
    private String thumbnailPath;

    @JsonProperty("storage_path")
    @Schema(description = "Storage path of the duplicate image", example = "uploads/scans/123/IMG_0001.JPG")
    private String storagePath;

    @JsonProperty("duplicate_type")
    @Schema(description = "Type of duplicate detection ('exact' or 'near')", example = "exact")
    private String duplicateType;
}
